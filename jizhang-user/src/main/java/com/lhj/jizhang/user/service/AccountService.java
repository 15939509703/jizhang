package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.common.util.BusinessIdGenerator;
import com.lhj.jizhang.user.dto.AccountAdjustInDTO;
import com.lhj.jizhang.user.dto.AccountCreateInDTO;
import com.lhj.jizhang.user.dto.AccountEntryOutDTO;
import com.lhj.jizhang.user.dto.AccountEntryPageOutDTO;
import com.lhj.jizhang.user.dto.AccountOutDTO;
import com.lhj.jizhang.user.dto.AccountSortInDTO;
import com.lhj.jizhang.user.dto.AccountUpdateInDTO;
import com.lhj.jizhang.user.entity.AccountEntity;
import com.lhj.jizhang.user.entity.AccountEntryEntity;
import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.entity.TransactionEntity;
import com.lhj.jizhang.user.mapper.AccountEntryMapper;
import com.lhj.jizhang.user.mapper.AccountMapper;
import com.lhj.jizhang.user.mapper.BookMapper;
import com.lhj.jizhang.user.mapper.TransactionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AccountService {
    private final AccountMapper accountMapper;
    private final AccountEntryMapper accountEntryMapper;
    private final TransactionMapper transactionMapper;
    private final BookMapper bookMapper;
    private final BookAccessService bookAccessService;

    public AccountService(AccountMapper accountMapper, AccountEntryMapper accountEntryMapper,
                          TransactionMapper transactionMapper, BookMapper bookMapper,
                          BookAccessService bookAccessService) {
        this.accountMapper = accountMapper;
        this.accountEntryMapper = accountEntryMapper;
        this.transactionMapper = transactionMapper;
        this.bookMapper = bookMapper;
        this.bookAccessService = bookAccessService;
    }

    public List<AccountOutDTO> list(Long userId, Long bookId) {
        bookAccessService.requireMember(userId, bookId);
        return accountMapper.selectList(Wrappers.<AccountEntity>lambdaQuery()
                        .eq(AccountEntity::getBookId, bookId)
                        .eq(AccountEntity::getDeletedFlag, 0)
                        .orderByAsc(AccountEntity::getSortNo, AccountEntity::getId)).stream()
                .map(this::toOutput).toList();
    }

    public AccountOutDTO get(Long userId, Long accountId) {
        AccountEntity account = requireAccount(accountId);
        bookAccessService.requireMember(userId, account.getBookId());
        return toOutput(account);
    }

    @Transactional
    public AccountOutDTO create(Long userId, AccountCreateInDTO input) {
        bookAccessService.requireWritable(userId, input.bookId());
        AccountEntity account = new AccountEntity();
        account.setAccountNo(BusinessIdGenerator.next("ACC_"));
        account.setBookId(input.bookId());
        account.setName(input.name().trim());
        account.setAccountType(input.accountType());
        account.setAccountNature(input.accountNature());
        account.setInitialBalance(input.initialBalance());
        account.setCurrentBalance(input.initialBalance());
        account.setIncludedInAssets(Boolean.TRUE.equals(input.includedInAssets()) ? 1 : 0);
        account.setSortNo(nextSortNo(input.bookId()));
        account.setStatus(1);
        account.setVersion(0);
        account.setDeletedFlag(0);
        account.setCreator(String.valueOf(userId));
        account.setModifier(String.valueOf(userId));
        accountMapper.insert(account);
        return toOutput(account);
    }

    @Transactional
    public AccountOutDTO update(Long userId, Long accountId, AccountUpdateInDTO input) {
        AccountEntity account = requireAccount(accountId);
        bookAccessService.requireWritable(userId, account.getBookId());
        account.setName(input.name().trim());
        account.setModifier(String.valueOf(userId));
        accountMapper.updateById(account);
        return toOutput(account);
    }

    @Transactional
    public void sort(Long userId, AccountSortInDTO input) {
        bookAccessService.requireWritable(userId, input.bookId());
        validateSortItems(input);
        for (var item : input.items()) {
            AccountEntity account = requireAccount(item.accountId());
            if (!input.bookId().equals(account.getBookId())) {
                throw new BusinessException(ErrorCodes.ACCOUNT_INVALID, "账户不属于当前账本");
            }
            account.setSortNo(item.sortNo());
            account.setModifier(String.valueOf(userId));
            accountMapper.updateById(account);
        }
    }

    @Transactional
    public void delete(Long userId, Long accountId) {
        AccountEntity account = requireAccount(accountId);
        bookAccessService.requireWritable(userId, account.getBookId());
        account.setDeletedFlag(1);
        account.setStatus(0);
        account.setModifier(String.valueOf(userId));
        accountMapper.updateById(account);
    }

    @Transactional
    public AccountOutDTO adjust(Long userId, Long accountId, AccountAdjustInDTO input) {
        AccountEntity account = accountMapper.selectByIdForUpdate(accountId);
        if (account == null || account.getDeletedFlag() != 0 || account.getStatus() != 1) {
            throw new BusinessException(ErrorCodes.ACCOUNT_INVALID, "账户不存在或不可用");
        }
        bookAccessService.requireWritable(userId, account.getBookId());
        TransactionEntity existing = findAdjustment(userId, account.getBookId(), input.requestId());
        if (existing != null) {
            return toOutput(account);
        }
        BigDecimal change = input.targetBalance().subtract(account.getCurrentBalance());
        if (change.compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "目标余额与当前余额相同");
        }
        TransactionEntity transaction = createAdjustment(userId, account, input, change.abs());
        transactionMapper.insert(transaction);
        accountEntryMapper.insert(buildAdjustmentEntry(userId, transaction, account, change));
        account.setCurrentBalance(input.targetBalance());
        account.setVersion(account.getVersion() + 1);
        account.setModifier(String.valueOf(userId));
        accountMapper.updateById(account);
        return toOutput(account);
    }

    public AccountEntryPageOutDTO entries(Long userId, Long accountId, Long cursorId, Integer limit) {
        AccountEntity account = requireAccount(accountId);
        bookAccessService.requireMember(userId, account.getBookId());
        int pageSize = limit == null ? 20 : limit;
        if (pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "每页数量必须在1到100之间");
        }
        var query = Wrappers.<AccountEntryEntity>lambdaQuery()
                .eq(AccountEntryEntity::getAccountId, accountId)
                .orderByDesc(AccountEntryEntity::getId)
                .last("LIMIT " + (pageSize + 1));
        if (cursorId != null) {
            query.lt(AccountEntryEntity::getId, cursorId);
        }
        List<AccountEntryEntity> rows = accountEntryMapper.selectList(query);
        boolean hasMore = rows.size() > pageSize;
        List<AccountEntryEntity> page = hasMore ? rows.subList(0, pageSize) : rows;
        Map<Long, TransactionEntity> transactions = loadTransactions(page);
        List<AccountEntryOutDTO> items = page.stream().map(entry -> toEntryOutput(entry,
                transactions.get(entry.getTransactionId()))).toList();
        Long nextCursor = hasMore && !page.isEmpty() ? page.getLast().getId() : null;
        return new AccountEntryPageOutDTO(items, nextCursor, hasMore);
    }

    private AccountEntity requireAccount(Long accountId) {
        AccountEntity account = accountMapper.selectById(accountId);
        if (account == null || account.getDeletedFlag() != 0) {
            throw new BusinessException(ErrorCodes.ACCOUNT_INVALID, "账户不存在");
        }
        return account;
    }

    private void validateSortItems(AccountSortInDTO input) {
        Set<Long> accountIds = new HashSet<>();
        for (var item : input.items()) {
            if (!accountIds.add(item.accountId())) {
                throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "账户排序列表不能重复");
            }
            if (item.sortNo() < 0) {
                throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "账户排序号不能小于0");
            }
        }
    }

    private int nextSortNo(Long bookId) {
        AccountEntity last = accountMapper.selectOne(Wrappers.<AccountEntity>lambdaQuery()
                .eq(AccountEntity::getBookId, bookId).eq(AccountEntity::getDeletedFlag, 0)
                .orderByDesc(AccountEntity::getSortNo).last("LIMIT 1"));
        return last == null ? 10 : last.getSortNo() + 10;
    }

    private TransactionEntity findAdjustment(Long userId, Long bookId, String requestId) {
        return transactionMapper.selectOne(Wrappers.<TransactionEntity>lambdaQuery()
                .eq(TransactionEntity::getBookId, bookId)
                .eq(TransactionEntity::getCreatedUserId, userId)
                .eq(TransactionEntity::getRequestId, requestId));
    }

    private TransactionEntity createAdjustment(Long userId, AccountEntity account, AccountAdjustInDTO input,
                                                BigDecimal amount) {
        BookEntity book = bookMapper.selectById(account.getBookId());
        TransactionEntity transaction = new TransactionEntity();
        transaction.setTransactionNo(BusinessIdGenerator.next("TXN_"));
        transaction.setRequestId(input.requestId());
        transaction.setBookId(account.getBookId());
        transaction.setCreatedUserId(userId);
        transaction.setTransactionType("ADJUSTMENT");
        transaction.setAmount(amount);
        transaction.setCurrencyCode(book.getCurrencyCode());
        transaction.setHappenedAt(LocalDateTime.now(ZoneOffset.UTC));
        transaction.setTitle("调整" + account.getName() + "余额");
        transaction.setNote(input.note());
        transaction.setStatus("EFFECTIVE");
        transaction.setVersion(0);
        transaction.setCreator(String.valueOf(userId));
        transaction.setModifier(String.valueOf(userId));
        return transaction;
    }

    private AccountEntryEntity buildAdjustmentEntry(Long userId, TransactionEntity transaction,
                                                     AccountEntity account, BigDecimal change) {
        AccountEntryEntity entry = new AccountEntryEntity();
        entry.setTransactionId(transaction.getId());
        entry.setAccountId(account.getId());
        entry.setEntryType(change.signum() > 0 ? "INCREASE" : "DECREASE");
        entry.setSignedAmount(change);
        entry.setBalanceBefore(account.getCurrentBalance());
        entry.setBalanceAfter(account.getCurrentBalance().add(change));
        entry.setHappenedAt(transaction.getHappenedAt());
        entry.setCreator(String.valueOf(userId));
        entry.setModifier(String.valueOf(userId));
        return entry;
    }

    private Map<Long, TransactionEntity> loadTransactions(List<AccountEntryEntity> entries) {
        if (entries.isEmpty()) {
            return Map.of();
        }
        return transactionMapper.selectByIds(entries.stream().map(AccountEntryEntity::getTransactionId).distinct()
                        .toList()).stream()
                .collect(Collectors.toMap(TransactionEntity::getId, Function.identity()));
    }

    private AccountEntryOutDTO toEntryOutput(AccountEntryEntity entry, TransactionEntity transaction) {
        return new AccountEntryOutDTO(entry.getId(), entry.getTransactionId(), transaction.getTitle(),
                transaction.getTransactionType(), entry.getSignedAmount(), entry.getBalanceBefore(),
                entry.getBalanceAfter(), entry.getHappenedAt().toInstant(ZoneOffset.UTC));
    }

    private AccountOutDTO toOutput(AccountEntity account) {
        return new AccountOutDTO(account.getId(), account.getAccountNo(), account.getBookId(), account.getName(),
                account.getAccountType(), account.getAccountNature(), account.getInitialBalance(),
                account.getCurrentBalance(), account.getIncludedInAssets() == 1, account.getSortNo(),
                account.getStatus(), account.getVersion(), toInstant(account.getCreatedTime()));
    }

    private java.time.Instant toInstant(LocalDateTime time) {
        return time == null ? null : time.toInstant(ZoneOffset.UTC);
    }
}
