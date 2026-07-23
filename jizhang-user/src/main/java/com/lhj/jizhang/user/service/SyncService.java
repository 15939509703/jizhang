package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhj.jizhang.user.dto.AccountOutDTO;
import com.lhj.jizhang.user.dto.BookOutDTO;
import com.lhj.jizhang.user.dto.BudgetSyncOutDTO;
import com.lhj.jizhang.user.dto.CategoryOutDTO;
import com.lhj.jizhang.user.dto.SyncChangesOutDTO;
import com.lhj.jizhang.user.dto.TransactionEntryOutDTO;
import com.lhj.jizhang.user.dto.TransactionOutDTO;
import com.lhj.jizhang.user.entity.AccountEntity;
import com.lhj.jizhang.user.entity.AccountEntryEntity;
import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.entity.BookMemberEntity;
import com.lhj.jizhang.user.entity.BudgetEntity;
import com.lhj.jizhang.user.entity.CategoryEntity;
import com.lhj.jizhang.user.entity.LoanRecordEntity;
import com.lhj.jizhang.user.entity.TransactionEntity;
import com.lhj.jizhang.user.mapper.AccountEntryMapper;
import com.lhj.jizhang.user.mapper.AccountMapper;
import com.lhj.jizhang.user.mapper.BookMapper;
import com.lhj.jizhang.user.mapper.BudgetMapper;
import com.lhj.jizhang.user.mapper.CategoryMapper;
import com.lhj.jizhang.user.mapper.LoanRecordMapper;
import com.lhj.jizhang.user.mapper.TransactionMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SyncService {
    private final BookMapper bookMapper;
    private final CategoryMapper categoryMapper;
    private final AccountMapper accountMapper;
    private final TransactionMapper transactionMapper;
    private final AccountEntryMapper accountEntryMapper;
    private final BudgetMapper budgetMapper;
    private final LoanRecordMapper loanRecordMapper;
    private final BookAccessService bookAccessService;
    private final LoanService loanService;

    public SyncService(
            BookMapper bookMapper,
            CategoryMapper categoryMapper,
            AccountMapper accountMapper,
            TransactionMapper transactionMapper,
            AccountEntryMapper accountEntryMapper,
            BudgetMapper budgetMapper,
            LoanRecordMapper loanRecordMapper,
            BookAccessService bookAccessService,
            LoanService loanService
    ) {
        this.bookMapper = bookMapper;
        this.categoryMapper = categoryMapper;
        this.accountMapper = accountMapper;
        this.transactionMapper = transactionMapper;
        this.accountEntryMapper = accountEntryMapper;
        this.budgetMapper = budgetMapper;
        this.loanRecordMapper = loanRecordMapper;
        this.bookAccessService = bookAccessService;
        this.loanService = loanService;
    }

    public SyncChangesOutDTO changes(Long userId, Long bookId, Instant cursor) {
        BookMemberEntity member = bookAccessService.requireMember(userId, bookId);
        LocalDateTime since = cursor == null ? LocalDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC) : toUtc(cursor);
        BookEntity book = bookMapper.selectById(bookId);
        List<CategoryEntity> categories = modifiedCategories(bookId, since);
        List<AccountEntity> accounts = modifiedAccounts(bookId, since);
        List<TransactionEntity> transactions = modifiedTransactions(bookId, since);
        List<BudgetEntity> budgets = modifiedBudgets(bookId, since);
        List<LoanRecordEntity> loans = modifiedLoans(bookId, since);
        return new SyncChangesOutDTO(Instant.now().toString(),
                book == null || !isChanged(book.getModifiedTime(), since) ? List.of() : List.of(toBook(book, member)),
                categories.stream().map(this::toCategory).toList(),
                accounts.stream().map(this::toAccount).toList(),
                toTransactions(transactions),
                budgets.stream().map(this::toBudget).toList(),
                loans.stream().map(loanService::toOutput).toList());
    }

    private List<CategoryEntity> modifiedCategories(Long bookId, LocalDateTime since) {
        return categoryMapper.selectList(Wrappers.<CategoryEntity>lambdaQuery()
                .eq(CategoryEntity::getBookId, bookId)
                .gt(CategoryEntity::getModifiedTime, since)
                .orderByAsc(CategoryEntity::getModifiedTime, CategoryEntity::getId)
                .last("LIMIT 200"));
    }

    private List<AccountEntity> modifiedAccounts(Long bookId, LocalDateTime since) {
        return accountMapper.selectList(Wrappers.<AccountEntity>lambdaQuery()
                .eq(AccountEntity::getBookId, bookId)
                .gt(AccountEntity::getModifiedTime, since)
                .orderByAsc(AccountEntity::getModifiedTime, AccountEntity::getId)
                .last("LIMIT 200"));
    }

    private List<TransactionEntity> modifiedTransactions(Long bookId, LocalDateTime since) {
        return transactionMapper.selectList(Wrappers.<TransactionEntity>lambdaQuery()
                .eq(TransactionEntity::getBookId, bookId)
                .gt(TransactionEntity::getModifiedTime, since)
                .orderByAsc(TransactionEntity::getModifiedTime, TransactionEntity::getId)
                .last("LIMIT 200"));
    }

    private List<BudgetEntity> modifiedBudgets(Long bookId, LocalDateTime since) {
        return budgetMapper.selectList(Wrappers.<BudgetEntity>lambdaQuery()
                .eq(BudgetEntity::getBookId, bookId)
                .gt(BudgetEntity::getModifiedTime, since)
                .orderByAsc(BudgetEntity::getModifiedTime, BudgetEntity::getId)
                .last("LIMIT 200"));
    }

    private List<LoanRecordEntity> modifiedLoans(Long bookId, LocalDateTime since) {
        return loanRecordMapper.selectList(Wrappers.<LoanRecordEntity>lambdaQuery()
                .eq(LoanRecordEntity::getBookId, bookId)
                .gt(LoanRecordEntity::getModifiedTime, since)
                .orderByAsc(LoanRecordEntity::getModifiedTime, LoanRecordEntity::getId)
                .last("LIMIT 200"));
    }

    private List<TransactionOutDTO> toTransactions(List<TransactionEntity> transactions) {
        Map<Long, List<AccountEntryEntity>> entries = loadEntries(transactions);
        return transactions.stream().map(transaction -> toTransaction(transaction, entries.get(transaction.getId())))
                .toList();
    }

    private Map<Long, List<AccountEntryEntity>> loadEntries(List<TransactionEntity> transactions) {
        if (transactions.isEmpty()) {
            return Map.of();
        }
        return accountEntryMapper.selectList(Wrappers.<AccountEntryEntity>lambdaQuery()
                        .in(AccountEntryEntity::getTransactionId,
                                transactions.stream().map(TransactionEntity::getId).toList())
                        .orderByAsc(AccountEntryEntity::getId)).stream()
                .collect(Collectors.groupingBy(AccountEntryEntity::getTransactionId, LinkedHashMap::new,
                        Collectors.toList()));
    }

    private TransactionOutDTO toTransaction(TransactionEntity transaction, List<AccountEntryEntity> entries) {
        List<TransactionEntryOutDTO> entryOutputs = entries == null ? List.of() : entries.stream()
                .sorted(Comparator.comparing(AccountEntryEntity::getId))
                .map(entry -> new TransactionEntryOutDTO(entry.getId(), entry.getAccountId(), entry.getEntryType(),
                        entry.getSignedAmount(), entry.getBalanceBefore(), entry.getBalanceAfter()))
                .toList();
        return new TransactionOutDTO(transaction.getId(), transaction.getTransactionNo(), transaction.getRequestId(),
                transaction.getBookId(), transaction.getCreatedUserId(), transaction.getTransactionType(),
                transaction.getCategoryId(), transaction.getOriginalTransactionId(), transaction.getAmount(),
                transaction.getCurrencyCode(), toInstant(transaction.getHappenedAt()), transaction.getTitle(),
                transaction.getNote(), transaction.getStatus(), transaction.getVersion(), entryOutputs, List.of());
    }

    private BookOutDTO toBook(BookEntity book, BookMemberEntity member) {
        return new BookOutDTO(book.getId(), book.getBookNo(), book.getName(), book.getDescription(),
                book.getCoverUrl(), book.getCurrencyCode(), book.getTimezone(), member.getRole());
    }

    private CategoryOutDTO toCategory(CategoryEntity category) {
        return new CategoryOutDTO(category.getId(), category.getCategoryNo(), category.getCategoryType(),
                category.getParentId(), category.getName(), category.getIcon(), category.getColor(),
                category.getSortNo(), category.getHiddenFlag() == 1, category.getSystemFlag() == 1);
    }

    private AccountOutDTO toAccount(AccountEntity account) {
        return new AccountOutDTO(account.getId(), account.getAccountNo(), account.getBookId(), account.getName(),
                account.getAccountType(), account.getAccountNature(), account.getInitialBalance(),
                account.getCurrentBalance(), account.getIncludedInAssets() == 1, account.getStatus(),
                account.getVersion());
    }

    private BudgetSyncOutDTO toBudget(BudgetEntity budget) {
        return new BudgetSyncOutDTO(budget.getId(), budget.getBudgetNo(), budget.getBookId(), budget.getBudgetMonth(),
                budget.getTotalLimit(), budget.getWarningRate(), budget.getStatus(), budget.getVersion(),
                toInstant(budget.getModifiedTime()));
    }

    private boolean isChanged(LocalDateTime modifiedTime, LocalDateTime since) {
        return modifiedTime != null && modifiedTime.isAfter(since);
    }

    private LocalDateTime toUtc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private Instant toInstant(LocalDateTime time) {
        return time == null ? null : time.toInstant(ZoneOffset.UTC);
    }
}
