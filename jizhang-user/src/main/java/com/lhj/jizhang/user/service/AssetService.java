package com.lhj.jizhang.user.service;

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
import com.lhj.jizhang.user.mapper.AccountEntryMapper;
import com.lhj.jizhang.user.mapper.AccountMapper;
import com.lhj.jizhang.user.mapper.BookMapper;
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

    public AssetService(AccountMapper accountMapper, AccountEntryMapper accountEntryMapper,
                        BookMapper bookMapper, BookAccessService bookAccessService) {
        this.accountMapper = accountMapper;
        this.accountEntryMapper = accountEntryMapper;
        this.bookMapper = bookMapper;
        this.bookAccessService = bookAccessService;
    }

    public AssetSummaryOutDTO summary(Long userId, Long bookId) {
        bookAccessService.requireMember(userId, bookId);
        BookEntity book = requireBook(bookId);
        List<AccountEntity> accounts = currentAccounts(bookId);
        BigDecimal totalAssets = accounts.stream().filter(account -> "ASSET".equals(account.getAccountNature()))
                .map(AccountEntity::getCurrentBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal liabilities = accounts.stream().filter(account -> "LIABILITY".equals(account.getAccountNature()))
                .map(AccountEntity::getCurrentBalance).filter(balance -> balance.signum() < 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add).abs();
        BigDecimal netAssets = accounts.stream().map(AccountEntity::getCurrentBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new AssetSummaryOutDTO(bookId, book.getCurrencyCode(), totalAssets, liabilities, netAssets,
                mapAccounts(accounts, "ASSET"), mapAccounts(accounts, "LIABILITY"));
    }

    public AssetTrendOutDTO trend(Long userId, Long bookId, Integer months) {
        bookAccessService.requireMember(userId, bookId);
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
        List<AssetTrendPointOutDTO> points = targetMonths.stream()
                .map(month -> new AssetTrendPointOutDTO(month.toString(), balanceAt(accounts, entries,
                        month.equals(current) ? Instant.now() : monthEnd(month, zone))))
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

    private BigDecimal balanceAt(List<AccountEntity> accounts,
                                 Map<Long, List<AccountEntryEntity>> entries, Instant cutoff) {
        BigDecimal result = BigDecimal.ZERO;
        for (AccountEntity account : accounts) {
            if (!existedAt(account, cutoff)) {
                continue;
            }
            BigDecimal afterCutoff = entries.getOrDefault(account.getId(), List.of()).stream()
                    .filter(entry -> entry.getHappenedAt().toInstant(ZoneOffset.UTC).isAfter(cutoff))
                    .map(AccountEntryEntity::getSignedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            result = result.add(account.getCurrentBalance().subtract(afterCutoff));
        }
        return result;
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
                        account.getAccountNature(), account.getCurrentBalance(), account.getModifiedTime() == null
                        ? null : account.getModifiedTime().toInstant(ZoneOffset.UTC))).toList();
    }

    private BookEntity requireBook(Long bookId) {
        BookEntity book = bookMapper.selectById(bookId);
        if (book == null || book.getStatus() != 1) {
            throw new BusinessException(ErrorCodes.BOOK_NOT_FOUND, "账本不存在或不可用");
        }
        return book;
    }
}
