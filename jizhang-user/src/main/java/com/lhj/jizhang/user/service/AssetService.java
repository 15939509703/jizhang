package com.lhj.jizhang.user.service;

import com.lhj.jizhang.user.model.BookMenu;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.user.dto.AssetAccountOutDTO;
import com.lhj.jizhang.user.dto.AssetSummaryOutDTO;
import com.lhj.jizhang.user.dto.AssetTrendOutDTO;
import com.lhj.jizhang.user.dto.AssetTrendPointOutDTO;
import com.lhj.jizhang.user.entity.AccountEntity;
import com.lhj.jizhang.user.entity.AccountEntryEntity;
import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.entity.LoanRecordEntity;
import com.lhj.jizhang.user.entity.TransactionEntity;
import com.lhj.jizhang.user.mapper.AccountEntryMapper;
import com.lhj.jizhang.user.mapper.AccountMapper;
import com.lhj.jizhang.user.mapper.BookMapper;
import com.lhj.jizhang.user.mapper.LoanRecordMapper;
import com.lhj.jizhang.user.mapper.TransactionMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AssetService {
    private final AccountMapper accountMapper;
    private final AccountEntryMapper accountEntryMapper;
    private final BookMapper bookMapper;
    private final BookAccessService bookAccessService;
    private final LoanRecordMapper loanRecordMapper;
    private final TransactionMapper transactionMapper;

    public AssetService(AccountMapper accountMapper, AccountEntryMapper accountEntryMapper,
                        BookMapper bookMapper, BookAccessService bookAccessService,
                        LoanRecordMapper loanRecordMapper, TransactionMapper transactionMapper) {
        this.accountMapper = accountMapper;
        this.accountEntryMapper = accountEntryMapper;
        this.bookMapper = bookMapper;
        this.bookAccessService = bookAccessService;
        this.loanRecordMapper = loanRecordMapper;
        this.transactionMapper = transactionMapper;
    }

    public AssetSummaryOutDTO summary(Long userId, Long bookId) {
        bookAccessService.requireMenu(userId, bookId, BookMenu.ASSETS);
        BookEntity book = requireBook(bookId);
        List<AccountEntity> accounts = currentAccounts(bookId);
        BigDecimal totalAssets = accounts.stream().filter(account -> "ASSET".equals(account.getAccountNature()))
                .map(AccountEntity::getCurrentBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<AccountEntity> liabilityAccounts = accounts.stream()
                .filter(account -> "LIABILITY".equals(account.getAccountNature())).toList();
        BigDecimal totalCreditLimit = liabilityAccounts.stream().map(this::creditLimit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal creditLiabilities = liabilityAccounts.stream()
                .map(account -> liabilityAmount(account, account.getCurrentBalance()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal liabilities = creditLiabilities.add(borrowedOutstanding(loadBorrowedLoans(bookId)));
        BigDecimal netAssets = totalAssets.subtract(liabilities);
        return new AssetSummaryOutDTO(bookId, book.getCurrencyCode(), totalAssets, totalCreditLimit, liabilities,
                netAssets, mapAccounts(accounts, "ASSET"), mapAccounts(accounts, "LIABILITY"));
    }

    public AssetTrendOutDTO trend(Long userId, Long bookId, Integer months) {
        bookAccessService.requireMenu(userId, bookId, BookMenu.ASSETS);
        BookEntity book = requireBook(bookId);
        int count = months == null ? 6 : months;
        if (count < 1 || count > 24) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "趋势月份必须在1到24之间");
        }
        ZoneId zone = ZoneId.of(book.getTimezone());
        YearMonth current = YearMonth.now(zone);
        List<YearMonth> targetMonths = targetMonths(current, count);
        List<AccountEntity> accounts = allIncludedAccounts(bookId);
        Map<Long, List<AccountEntryEntity>> entries = loadEntries(accounts, monthEnd(targetMonths.getFirst(), zone));
        List<LoanRecordEntity> loans = loadBorrowedLoans(bookId);
        Map<Long, List<TransactionEntity>> repayments = loadRepayments(loans, monthEnd(targetMonths.getFirst(), zone));
        List<AssetTrendPointOutDTO> points = targetMonths.stream()
                .map(month -> {
                    Instant cutoff = month.equals(current) ? Instant.now() : monthEnd(month, zone);
                    return new AssetTrendPointOutDTO(month.toString(), netAssetsAt(accounts, entries, cutoff)
                            .subtract(borrowedOutstandingAt(loans, repayments, cutoff)));
                })
                .toList();
        return new AssetTrendOutDTO(bookId, book.getCurrencyCode(), points);
    }

    private List<YearMonth> targetMonths(YearMonth current, int count) {
        List<YearMonth> months = new ArrayList<>();
        for (int index = count - 1; index >= 0; index--) {
            months.add(current.minusMonths(index));
        }
        return months;
    }

    private List<AccountEntity> currentAccounts(Long bookId) {
        return accountMapper.selectList(Wrappers.<AccountEntity>lambdaQuery()
                .eq(AccountEntity::getBookId, bookId)
                .eq(AccountEntity::getIncludedInAssets, 1)
                .eq(AccountEntity::getStatus, 1)
                .eq(AccountEntity::getDeletedFlag, 0)
                .orderByAsc(AccountEntity::getSortNo, AccountEntity::getId));
    }

    private List<AccountEntity> allIncludedAccounts(Long bookId) {
        return accountMapper.selectList(Wrappers.<AccountEntity>lambdaQuery()
                .eq(AccountEntity::getBookId, bookId)
                .eq(AccountEntity::getIncludedInAssets, 1)
                .orderByAsc(AccountEntity::getId));
    }

    private Map<Long, List<AccountEntryEntity>> loadEntries(List<AccountEntity> accounts, Instant earliest) {
        if (accounts.isEmpty()) {
            return Map.of();
        }
        return accountEntryMapper.selectList(Wrappers.<AccountEntryEntity>lambdaQuery()
                        .in(AccountEntryEntity::getAccountId, accounts.stream().map(AccountEntity::getId).toList())
                        .gt(AccountEntryEntity::getHappenedAt, LocalDateTime.ofInstant(earliest, ZoneOffset.UTC))
                        .orderByAsc(AccountEntryEntity::getHappenedAt, AccountEntryEntity::getId)).stream()
                .collect(Collectors.groupingBy(AccountEntryEntity::getAccountId));
    }

    /** 读取借入记录，包含已结清记录以还原历史月份负债。 */
    private List<LoanRecordEntity> loadBorrowedLoans(Long bookId) {
        return loanRecordMapper.selectList(Wrappers.<LoanRecordEntity>lambdaQuery()
                .eq(LoanRecordEntity::getBookId, bookId)
                .eq(LoanRecordEntity::getLoanType, "BORROW"));
    }

    /** 读取首个趋势月末之后的归还流水，用于回推各月未还本金。 */
    private Map<Long, List<TransactionEntity>> loadRepayments(List<LoanRecordEntity> loans, Instant earliest) {
        List<Long> transactionIds = loans.stream().map(LoanRecordEntity::getRelatedTransactionId)
                .filter(id -> id != null).toList();
        if (transactionIds.isEmpty()) {
            return Map.of();
        }
        return transactionMapper.selectList(Wrappers.<TransactionEntity>lambdaQuery()
                        .in(TransactionEntity::getOriginalTransactionId, transactionIds)
                        .eq(TransactionEntity::getTransactionType, "REPAYMENT")
                        .eq(TransactionEntity::getStatus, "EFFECTIVE")
                        .gt(TransactionEntity::getHappenedAt, LocalDateTime.ofInstant(earliest, ZoneOffset.UTC)))
                .stream().collect(Collectors.groupingBy(TransactionEntity::getOriginalTransactionId));
    }

    /** 当前未还借入本金。 */
    private BigDecimal borrowedOutstanding(List<LoanRecordEntity> loans) {
        return loans.stream().map(loan -> loan.getTotalAmount().subtract(loan.getRepaidAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 截止时点未还借入本金；之后的归还金额加回当前余额。 */
    private BigDecimal borrowedOutstandingAt(List<LoanRecordEntity> loans,
                                             Map<Long, List<TransactionEntity>> repayments, Instant cutoff) {
        return loans.stream().filter(loan -> loan.getCreatedTime() == null
                        || !loan.getCreatedTime().toInstant(ZoneOffset.UTC).isAfter(cutoff))
                .map(loan -> outstandingAt(loan, repayments, cutoff))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 历史未关联流水的借入记录按当前已还本金计算。 */
    private BigDecimal outstandingAt(LoanRecordEntity loan,
                                     Map<Long, List<TransactionEntity>> repayments, Instant cutoff) {
        List<TransactionEntity> entries = loan.getRelatedTransactionId() == null ? List.of()
                : repayments.getOrDefault(loan.getRelatedTransactionId(), List.of());
        BigDecimal laterRepayments = entries.stream()
                .filter(item -> item.getHappenedAt().toInstant(ZoneOffset.UTC).isAfter(cutoff))
                .map(TransactionEntity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return loan.getTotalAmount().subtract(loan.getRepaidAmount()).add(laterRepayments);
    }

    private BigDecimal netAssetsAt(List<AccountEntity> accounts,
                                   Map<Long, List<AccountEntryEntity>> entries, Instant cutoff) {
        BigDecimal totalAssets = BigDecimal.ZERO;
        BigDecimal liabilities = BigDecimal.ZERO;
        for (AccountEntity account : accounts) {
            if (!existedAt(account, cutoff)) {
                continue;
            }
            BigDecimal afterCutoff = entries.getOrDefault(account.getId(), List.of()).stream()
                    .filter(entry -> entry.getHappenedAt().toInstant(ZoneOffset.UTC).isAfter(cutoff))
                    .map(AccountEntryEntity::getSignedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal balance = account.getCurrentBalance().subtract(afterCutoff);
            if ("LIABILITY".equals(account.getAccountNature())) {
                liabilities = liabilities.add(liabilityAmount(account, balance));
            } else {
                totalAssets = totalAssets.add(balance);
            }
        }
        return totalAssets.subtract(liabilities);
    }

    private BigDecimal liabilityAmount(AccountEntity account, BigDecimal availableBalance) {
        return creditLimit(account).subtract(availableBalance).max(BigDecimal.ZERO);
    }

    private BigDecimal creditLimit(AccountEntity account) {
        return account.getInitialBalance() == null ? BigDecimal.ZERO : account.getInitialBalance();
    }

    private boolean existedAt(AccountEntity account, Instant cutoff) {
        Instant createdAt = account.getCreatedTime() == null ? Instant.EPOCH
                : account.getCreatedTime().toInstant(ZoneOffset.UTC);
        Instant archivedAt = account.getArchivedTime() == null ? null
                : account.getArchivedTime().toInstant(ZoneOffset.UTC);
        return !createdAt.isAfter(cutoff) && (archivedAt == null || archivedAt.isAfter(cutoff));
    }

    private Instant monthEnd(YearMonth month, ZoneId zone) {
        return month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().minusNanos(1);
    }

    private List<AssetAccountOutDTO> mapAccounts(List<AccountEntity> accounts, String nature) {
        return accounts.stream().filter(account -> nature.equals(account.getAccountNature()))
                .map(account -> new AssetAccountOutDTO(account.getId(), account.getName(), account.getAccountType(),
                        account.getAccountNature(), account.getInitialBalance(), account.getCurrentBalance(),
                        "LIABILITY".equals(nature) ? liabilityAmount(account, account.getCurrentBalance())
                                : BigDecimal.ZERO,
                        account.getModifiedTime() == null ? null
                                : account.getModifiedTime().toInstant(ZoneOffset.UTC))).toList();
    }

    private BookEntity requireBook(Long bookId) {
        BookEntity book = bookMapper.selectById(bookId);
        if (book == null || book.getStatus() != 1) {
            throw new BusinessException(ErrorCodes.BOOK_NOT_FOUND, "账本不存在或不可用");
        }
        return book;
    }
}
