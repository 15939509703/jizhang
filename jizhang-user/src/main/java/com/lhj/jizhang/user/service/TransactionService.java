package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.common.util.BusinessIdGenerator;
import com.lhj.jizhang.user.dto.TransactionCreateInDTO;
import com.lhj.jizhang.user.dto.TransactionEntryOutDTO;
import com.lhj.jizhang.user.dto.TransactionOutDTO;
import com.lhj.jizhang.user.dto.TransactionPageOutDTO;
import com.lhj.jizhang.user.dto.TransactionUpdateInDTO;
import com.lhj.jizhang.user.entity.AccountEntity;
import com.lhj.jizhang.user.entity.AccountEntryEntity;
import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.entity.CategoryEntity;
import com.lhj.jizhang.user.entity.TransactionEntity;
import com.lhj.jizhang.user.mapper.AccountEntryMapper;
import com.lhj.jizhang.user.mapper.AccountMapper;
import com.lhj.jizhang.user.mapper.BookMapper;
import com.lhj.jizhang.user.mapper.CategoryMapper;
import com.lhj.jizhang.user.mapper.TransactionMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TransactionService {
    private static final Set<String> TRANSACTION_TYPES = Set.of("EXPENSE", "INCOME", "TRANSFER");
    private static final Set<String> STATUSES = Set.of("EFFECTIVE", "VOIDED", "REVERSED");
    private static final Set<String> SORT_OPTIONS = Set.of("AMOUNT_DESC");

    private final TransactionMapper transactionMapper;
    private final AccountEntryMapper accountEntryMapper;
    private final AccountMapper accountMapper;
    private final CategoryMapper categoryMapper;
    private final BookMapper bookMapper;
    private final BookAccessService bookAccessService;

    public TransactionService(
            TransactionMapper transactionMapper,
            AccountEntryMapper accountEntryMapper,
            AccountMapper accountMapper,
            CategoryMapper categoryMapper,
            BookMapper bookMapper,
            BookAccessService bookAccessService
    ) {
        this.transactionMapper = transactionMapper;
        this.accountEntryMapper = accountEntryMapper;
        this.accountMapper = accountMapper;
        this.categoryMapper = categoryMapper;
        this.bookMapper = bookMapper;
        this.bookAccessService = bookAccessService;
    }

    @Transactional
    public TransactionOutDTO create(Long userId, TransactionCreateInDTO input) {
        bookAccessService.requireWritable(userId, input.bookId());
        validateInput(input);
        validateOriginal(input);
        Map<Long, AccountEntity> accounts = lockAccounts(input.bookId(), accountIds(input));
        TransactionEntity existing = findByRequestId(userId, input.bookId(), input.requestId());
        if (existing != null) {
            return toOutput(existing, loadEntries(List.of(existing.getId())).get(existing.getId()));
        }
        validateCategory(input);
        TransactionEntity transaction = buildTransaction(userId, input);
        TransactionEntity persisted = insertTransaction(transaction, userId, input);
        if (persisted != transaction) {
            return toOutput(persisted, loadEntries(List.of(persisted.getId())).get(persisted.getId()));
        }
        applyEntries(transaction, input, accounts, userId);
        return toOutput(transaction, loadEntries(List.of(transaction.getId())).get(transaction.getId()));
    }

    public TransactionPageOutDTO list(
            Long userId,
            Long bookId,
            String type,
            String status,
            Long categoryId,
            Long accountId,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String keyword,
            Instant startAt,
            Instant endAt,
            String sortBy,
            Long cursorId,
            Integer limit
    ) {
        bookAccessService.requireMember(userId, bookId);
        int pageSize = validateQuery(type, status, sortBy, limit);
        validateAmountRange(minAmount, maxAmount);
        var query = Wrappers.<TransactionEntity>lambdaQuery()
                .eq(TransactionEntity::getBookId, bookId)
                .last("LIMIT " + (pageSize + 1));
        if ("AMOUNT_DESC".equals(sortBy)) {
            query.orderByDesc(TransactionEntity::getAmount, TransactionEntity::getId);
        } else {
            query.orderByDesc(TransactionEntity::getId);
        }
        if (type != null) {
            query.eq(TransactionEntity::getTransactionType, type);
        } else {
            query.in(TransactionEntity::getTransactionType, TRANSACTION_TYPES);
        }
        if (status != null) {
            query.eq(TransactionEntity::getStatus, status);
        }
        if (categoryId != null) {
            query.eq(TransactionEntity::getCategoryId, categoryId);
        }
        if (accountId != null) {
            query.inSql(TransactionEntity::getId,
                    "SELECT transaction_id FROM fin_account_entry WHERE account_id = " + accountId);
        }
        if (minAmount != null) {
            query.ge(TransactionEntity::getAmount, minAmount);
        }
        if (maxAmount != null) {
            query.le(TransactionEntity::getAmount, maxAmount);
        }
        if (keyword != null && !keyword.isBlank()) {
            String normalized = keyword.trim();
            query.and(wrapper -> wrapper.like(TransactionEntity::getTitle, normalized)
                    .or().like(TransactionEntity::getNote, normalized));
        }
        if (startAt != null) {
            query.ge(TransactionEntity::getHappenedAt, toUtc(startAt));
        }
        if (endAt != null) {
            query.lt(TransactionEntity::getHappenedAt, toUtc(endAt));
        }
        if (cursorId != null) {
            query.lt(TransactionEntity::getId, cursorId);
        }
        List<TransactionEntity> rows = transactionMapper.selectList(query);
        boolean hasMore = rows.size() > pageSize;
        List<TransactionEntity> page = hasMore ? rows.subList(0, pageSize) : rows;
        Map<Long, List<AccountEntryEntity>> entries = loadEntries(page.stream().map(TransactionEntity::getId).toList());
        List<TransactionOutDTO> items = page.stream()
                .map(transaction -> toOutput(transaction, entries.get(transaction.getId())))
                .toList();
        Long nextCursor = hasMore && !page.isEmpty() ? page.getLast().getId() : null;
        return new TransactionPageOutDTO(items, nextCursor, hasMore);
    }

    public TransactionOutDTO get(Long userId, Long transactionId) {
        TransactionEntity transaction = transactionMapper.selectById(transactionId);
        if (transaction == null) {
            throw new BusinessException(ErrorCodes.TRANSACTION_INVALID, "账单不存在");
        }
        bookAccessService.requireMember(userId, transaction.getBookId());
        return toOutput(transaction, loadEntries(List.of(transactionId)).get(transactionId));
    }

    @Transactional
    public TransactionOutDTO update(Long userId, Long transactionId, TransactionUpdateInDTO input) {
        TransactionEntity transaction = transactionMapper.selectByIdForUpdate(transactionId);
        if (transaction == null) {
            throw new BusinessException(ErrorCodes.TRANSACTION_INVALID, "账单不存在");
        }
        bookAccessService.requireWritable(userId, transaction.getBookId());
        if (!"EFFECTIVE".equals(transaction.getStatus()) || !input.version().equals(transaction.getVersion())) {
            throw new BusinessException(ErrorCodes.TRANSACTION_CONFLICT, "账单已发生变化，请刷新后重试");
        }
        TransactionCreateInDTO command = updateCommand(transaction, input);
        validateInput(command);
        validateOriginal(command);
        validateCategory(command);
        List<AccountEntryEntity> originals = originalEntries(transactionId);
        Set<Long> accountIds = originals.stream().map(AccountEntryEntity::getAccountId).collect(Collectors.toSet());
        accountIds.addAll(accountIds(command));
        Map<Long, AccountEntity> accounts = lockAccounts(transaction.getBookId(), accountIds);
        reverseEntries(transaction, originals, accounts, userId);
        accountEntryMapper.delete(Wrappers.<AccountEntryEntity>lambdaQuery()
                .eq(AccountEntryEntity::getTransactionId, transactionId));
        applyTransactionChanges(transaction, input, userId);
        transactionMapper.updateById(transaction);
        applyEntries(transaction, command, accounts, userId);
        return toOutput(transaction, loadEntries(List.of(transactionId)).get(transactionId));
    }

    @Transactional
    public TransactionOutDTO voidTransaction(Long userId, Long transactionId) {
        TransactionEntity transaction = transactionMapper.selectByIdForUpdate(transactionId);
        if (transaction == null) {
            throw new BusinessException(ErrorCodes.TRANSACTION_INVALID, "账单不存在");
        }
        bookAccessService.requireWritable(userId, transaction.getBookId());
        if ("VOIDED".equals(transaction.getStatus())) {
            return toOutput(transaction, loadEntries(List.of(transactionId)).get(transactionId));
        }
        if (!"EFFECTIVE".equals(transaction.getStatus())) {
            throw new BusinessException(ErrorCodes.TRANSACTION_CONFLICT, "当前账单状态不允许作废");
        }
        List<AccountEntryEntity> originalEntries = originalEntries(transactionId);
        Map<Long, AccountEntity> accounts = lockAccounts(transaction.getBookId(), originalEntries.stream()
                .map(AccountEntryEntity::getAccountId).collect(Collectors.toSet()));
        reverseEntries(transaction, originalEntries, accounts, userId);
        transaction.setStatus("VOIDED");
        transaction.setVersion(transaction.getVersion() + 1);
        transaction.setModifier(String.valueOf(userId));
        transactionMapper.updateById(transaction);
        return toOutput(transaction, loadEntries(List.of(transactionId)).get(transactionId));
    }

    private void validateInput(TransactionCreateInDTO input) {
        if ("TRANSFER".equals(input.transactionType())) {
            if (input.targetAccountId() == null || input.accountId().equals(input.targetAccountId())) {
                throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "转账必须选择不同的转入账户");
            }
            if (input.categoryId() != null) {
                throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "转账不能选择收支分类");
            }
        } else if (input.categoryId() == null) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "收入和支出必须选择分类");
        }
    }

    private int validateQuery(String type, String status, String sortBy, Integer limit) {
        if (type != null && !TRANSACTION_TYPES.contains(type)) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "账单类型不正确");
        }
        if (status != null && !STATUSES.contains(status)) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "账单状态不正确");
        }
        if (sortBy != null && !SORT_OPTIONS.contains(sortBy)) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "排序方式不正确");
        }
        int pageSize = limit == null ? 20 : limit;
        if (pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "每页数量必须在1到100之间");
        }
        return pageSize;
    }

    private void validateAmountRange(BigDecimal minAmount, BigDecimal maxAmount) {
        if (minAmount != null && minAmount.compareTo(BigDecimal.ZERO) < 0
                || maxAmount != null && maxAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "筛选金额不能小于0");
        }
        if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "最小金额不能大于最大金额");
        }
    }

    private TransactionCreateInDTO updateCommand(TransactionEntity transaction, TransactionUpdateInDTO input) {
        return new TransactionCreateInDTO(transaction.getRequestId(), transaction.getBookId(),
                input.transactionType(), input.categoryId(), transaction.getOriginalTransactionId(),
                input.accountId(), input.targetAccountId(), input.amount(), input.happenedAt(),
                input.title(), input.note());
    }

    private void applyTransactionChanges(TransactionEntity transaction, TransactionUpdateInDTO input, Long userId) {
        transaction.setTransactionType(input.transactionType());
        transaction.setCategoryId(input.categoryId());
        transaction.setAmount(input.amount());
        transaction.setHappenedAt(toUtc(input.happenedAt()));
        transaction.setTitle(input.title().trim());
        transaction.setNote(input.note());
        transaction.setVersion(transaction.getVersion() + 1);
        transaction.setModifier(String.valueOf(userId));
    }

    private Collection<Long> accountIds(TransactionCreateInDTO input) {
        List<Long> accountIds = new ArrayList<>();
        accountIds.add(input.accountId());
        if (input.targetAccountId() != null) {
            accountIds.add(input.targetAccountId());
        }
        return accountIds;
    }

    private Map<Long, AccountEntity> lockAccounts(Long bookId, Collection<Long> accountIds) {
        Map<Long, AccountEntity> accounts = new LinkedHashMap<>();
        accountIds.stream().distinct().sorted().forEach(accountId -> {
            AccountEntity account = accountMapper.selectByIdForUpdate(accountId);
            if (account == null || !bookId.equals(account.getBookId()) || account.getStatus() != 1
                    || account.getDeletedFlag() != 0) {
                throw new BusinessException(ErrorCodes.ACCOUNT_INVALID, "账户不存在或不可用");
            }
            accounts.put(accountId, account);
        });
        return accounts;
    }

    private void validateCategory(TransactionCreateInDTO input) {
        if (input.categoryId() == null) {
            return;
        }
        CategoryEntity category = categoryMapper.selectById(input.categoryId());
        if (category == null || !input.bookId().equals(category.getBookId())
                || !input.transactionType().equals(category.getCategoryType())
                || category.getHiddenFlag() != 0 || category.getDeletedFlag() != 0) {
            throw new BusinessException(ErrorCodes.CATEGORY_INVALID, "分类不存在或与账单类型不匹配");
        }
    }

    private void validateOriginal(TransactionCreateInDTO input) {
        if (input.originalTransactionId() == null) {
            return;
        }
        TransactionEntity original = transactionMapper.selectById(input.originalTransactionId());
        if (original == null || !input.bookId().equals(original.getBookId())) {
            throw new BusinessException(ErrorCodes.TRANSACTION_INVALID, "复制来源账单不存在或不属于当前账本");
        }
    }

    private TransactionEntity findByRequestId(Long userId, Long bookId, String requestId) {
        return transactionMapper.selectOne(Wrappers.<TransactionEntity>lambdaQuery()
                .eq(TransactionEntity::getBookId, bookId)
                .eq(TransactionEntity::getCreatedUserId, userId)
                .eq(TransactionEntity::getRequestId, requestId));
    }

    private TransactionEntity buildTransaction(Long userId, TransactionCreateInDTO input) {
        BookEntity book = bookMapper.selectById(input.bookId());
        if (book == null || book.getStatus() != 1) {
            throw new BusinessException(ErrorCodes.BOOK_NOT_FOUND, "账本不存在或不可用");
        }
        TransactionEntity transaction = new TransactionEntity();
        transaction.setTransactionNo(BusinessIdGenerator.next("TXN_"));
        transaction.setRequestId(input.requestId());
        transaction.setBookId(input.bookId());
        transaction.setCreatedUserId(userId);
        transaction.setTransactionType(input.transactionType());
        transaction.setCategoryId(input.categoryId());
        transaction.setOriginalTransactionId(input.originalTransactionId());
        transaction.setAmount(input.amount());
        transaction.setCurrencyCode(book.getCurrencyCode());
        transaction.setHappenedAt(toUtc(input.happenedAt()));
        transaction.setTitle(input.title().trim());
        transaction.setNote(input.note());
        transaction.setStatus("EFFECTIVE");
        transaction.setVersion(0);
        transaction.setCreator(String.valueOf(userId));
        transaction.setModifier(String.valueOf(userId));
        return transaction;
    }

    private TransactionEntity insertTransaction(
            TransactionEntity transaction,
            Long userId,
            TransactionCreateInDTO input
    ) {
        try {
            transactionMapper.insert(transaction);
            return transaction;
        } catch (DuplicateKeyException exception) {
            TransactionEntity existing = findByRequestId(userId, input.bookId(), input.requestId());
            if (existing == null) {
                throw exception;
            }
            return existing;
        }
    }

    private void applyEntries(
            TransactionEntity transaction,
            TransactionCreateInDTO input,
            Map<Long, AccountEntity> accounts,
            Long userId
    ) {
        if ("INCOME".equals(input.transactionType())) {
            applyEntry(transaction, accounts.get(input.accountId()), input.amount(), "INCREASE", userId);
        } else {
            applyEntry(transaction, accounts.get(input.accountId()), input.amount().negate(), "DECREASE", userId);
        }
        if ("TRANSFER".equals(input.transactionType())) {
            applyEntry(transaction, accounts.get(input.targetAccountId()), input.amount(), "INCREASE", userId);
        }
    }

    private void applyEntry(
            TransactionEntity transaction,
            AccountEntity account,
            BigDecimal signedAmount,
            String entryType,
            Long userId
    ) {
        BigDecimal balanceBefore = account.getCurrentBalance();
        BigDecimal balanceAfter = balanceBefore.add(signedAmount);
        accountEntryMapper.insert(buildEntry(transaction, account.getId(), entryType, signedAmount,
                balanceBefore, balanceAfter, userId));
        updateAccountBalance(account, balanceAfter, userId);
    }

    private AccountEntryEntity buildEntry(
            TransactionEntity transaction,
            Long accountId,
            String entryType,
            BigDecimal signedAmount,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter,
            Long userId
    ) {
        AccountEntryEntity entry = new AccountEntryEntity();
        entry.setTransactionId(transaction.getId());
        entry.setAccountId(accountId);
        entry.setEntryType(entryType);
        entry.setSignedAmount(signedAmount);
        entry.setBalanceBefore(balanceBefore);
        entry.setBalanceAfter(balanceAfter);
        entry.setHappenedAt(transaction.getHappenedAt());
        entry.setCreator(String.valueOf(userId));
        entry.setModifier(String.valueOf(userId));
        return entry;
    }

    private void updateAccountBalance(AccountEntity account, BigDecimal balanceAfter, Long userId) {
        account.setCurrentBalance(balanceAfter);
        account.setVersion(account.getVersion() + 1);
        account.setModifier(String.valueOf(userId));
        accountMapper.updateById(account);
    }

    private List<AccountEntryEntity> originalEntries(Long transactionId) {
        return accountEntryMapper.selectList(Wrappers.<AccountEntryEntity>lambdaQuery()
                .eq(AccountEntryEntity::getTransactionId, transactionId)
                .ne(AccountEntryEntity::getEntryType, "REVERSAL")
                .orderByAsc(AccountEntryEntity::getId));
    }

    private void reverseEntries(
            TransactionEntity transaction,
            List<AccountEntryEntity> entries,
            Map<Long, AccountEntity> accounts,
            Long userId
    ) {
        for (AccountEntryEntity original : entries) {
            AccountEntity account = accounts.get(original.getAccountId());
            BigDecimal reversalAmount = original.getSignedAmount().negate();
            applyEntry(transaction, account, reversalAmount, "REVERSAL", userId);
        }
    }

    private Map<Long, List<AccountEntryEntity>> loadEntries(List<Long> transactionIds) {
        if (transactionIds.isEmpty()) {
            return Map.of();
        }
        return accountEntryMapper.selectList(Wrappers.<AccountEntryEntity>lambdaQuery()
                        .in(AccountEntryEntity::getTransactionId, transactionIds)
                        .orderByAsc(AccountEntryEntity::getId)).stream()
                .collect(Collectors.groupingBy(AccountEntryEntity::getTransactionId, LinkedHashMap::new,
                        Collectors.toList()));
    }

    private TransactionOutDTO toOutput(TransactionEntity transaction, List<AccountEntryEntity> entries) {
        List<TransactionEntryOutDTO> entryOutputs = entries == null ? List.of() : entries.stream()
                .sorted(Comparator.comparing(AccountEntryEntity::getId))
                .map(entry -> new TransactionEntryOutDTO(entry.getId(), entry.getAccountId(), entry.getEntryType(),
                        entry.getSignedAmount(), entry.getBalanceBefore(), entry.getBalanceAfter()))
                .toList();
        return new TransactionOutDTO(transaction.getId(), transaction.getTransactionNo(), transaction.getRequestId(),
                transaction.getBookId(), transaction.getCreatedUserId(), transaction.getTransactionType(),
                transaction.getCategoryId(), transaction.getOriginalTransactionId(), transaction.getAmount(),
                transaction.getCurrencyCode(),
                transaction.getHappenedAt().toInstant(ZoneOffset.UTC), transaction.getTitle(), transaction.getNote(),
                transaction.getStatus(), transaction.getVersion(), entryOutputs, List.of());
    }

    private LocalDateTime toUtc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
