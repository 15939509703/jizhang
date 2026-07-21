package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lhj.jizhang.user.dto.TransactionCreateInDTO;
import com.lhj.jizhang.user.dto.TransactionOutDTO;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {
    @Mock
    private TransactionMapper transactionMapper;
    @Mock
    private AccountEntryMapper accountEntryMapper;
    @Mock
    private AccountMapper accountMapper;
    @Mock
    private CategoryMapper categoryMapper;
    @Mock
    private BookMapper bookMapper;
    @Mock
    private BookAccessService bookAccessService;

    private TransactionService service;

    @BeforeEach
    void setUp() {
        service = new TransactionService(transactionMapper, accountEntryMapper, accountMapper,
                categoryMapper, bookMapper, bookAccessService);
    }

    @Test
    void shouldCreateExpenseAndDecreaseAccountBalance() {
        AccountEntity account = activeAccount(new BigDecimal("100.00"));
        List<AccountEntryEntity> entries = captureInsertedEntries();
        when(accountMapper.selectByIdForUpdate(10L)).thenReturn(account);
        when(transactionMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(categoryMapper.selectById(20L)).thenReturn(expenseCategory());
        when(bookMapper.selectById(1L)).thenReturn(activeBook());
        assignTransactionId();
        when(accountEntryMapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> entries);

        TransactionOutDTO result = service.create(7L, expenseInput("request-1"));

        assertEquals(100L, result.id());
        assertEquals(new BigDecimal("70.00"), account.getCurrentBalance());
        assertEquals(new BigDecimal("-30.00"), entries.getFirst().getSignedAmount());
        assertEquals(new BigDecimal("70.00"), entries.getFirst().getBalanceAfter());
        verify(accountMapper).updateById(account);
    }

    @Test
    void shouldReturnExistingTransactionForDuplicateRequestWithoutChangingBalance() {
        AccountEntity account = activeAccount(new BigDecimal("100.00"));
        TransactionEntity existing = effectiveTransaction();
        when(accountMapper.selectByIdForUpdate(10L)).thenReturn(account);
        when(transactionMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        when(accountEntryMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        TransactionOutDTO result = service.create(7L, expenseInput("request-1"));

        assertEquals(100L, result.id());
        assertEquals(new BigDecimal("100.00"), account.getCurrentBalance());
        verify(transactionMapper, never()).insert(any(TransactionEntity.class));
        verify(accountMapper, never()).updateById(any(AccountEntity.class));
    }

    @Test
    void shouldReverseAccountBalanceWhenVoidingTransaction() {
        TransactionEntity transaction = effectiveTransaction();
        AccountEntity account = activeAccount(new BigDecimal("70.00"));
        AccountEntryEntity original = originalExpenseEntry();
        List<AccountEntryEntity> allEntries = new ArrayList<>(List.of(original));
        when(transactionMapper.selectByIdForUpdate(100L)).thenReturn(transaction);
        when(accountEntryMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(original))
                .thenAnswer(invocation -> allEntries);
        when(accountMapper.selectByIdForUpdate(10L)).thenReturn(account);
        doAnswer(invocation -> {
            AccountEntryEntity entry = invocation.getArgument(0);
            entry.setId(502L);
            allEntries.add(entry);
            return 1;
        }).when(accountEntryMapper).insert(any(AccountEntryEntity.class));

        TransactionOutDTO result = service.voidTransaction(7L, 100L);

        assertEquals("VOIDED", result.status());
        assertEquals(new BigDecimal("100.00"), account.getCurrentBalance());
        assertEquals("REVERSAL", result.entries().getLast().entryType());
        assertEquals(new BigDecimal("30.00"), result.entries().getLast().signedAmount());
        verify(transactionMapper).updateById(transaction);
    }

    private List<AccountEntryEntity> captureInsertedEntries() {
        List<AccountEntryEntity> entries = new ArrayList<>();
        doAnswer(invocation -> {
            AccountEntryEntity entry = invocation.getArgument(0);
            entry.setId(501L + entries.size());
            entries.add(entry);
            return 1;
        }).when(accountEntryMapper).insert(any(AccountEntryEntity.class));
        return entries;
    }

    private void assignTransactionId() {
        doAnswer(invocation -> {
            invocation.<TransactionEntity>getArgument(0).setId(100L);
            return 1;
        }).when(transactionMapper).insert(any(TransactionEntity.class));
    }

    private TransactionCreateInDTO expenseInput(String requestId) {
        return new TransactionCreateInDTO(requestId, 1L, "EXPENSE", 20L, 10L, null,
                new BigDecimal("30.00"), Instant.parse("2026-07-21T10:00:00Z"), "午餐", "工作餐");
    }

    private AccountEntity activeAccount(BigDecimal balance) {
        AccountEntity account = new AccountEntity();
        account.setId(10L);
        account.setBookId(1L);
        account.setCurrentBalance(balance);
        account.setStatus(1);
        account.setVersion(0);
        account.setDeletedFlag(0);
        return account;
    }

    private CategoryEntity expenseCategory() {
        CategoryEntity category = new CategoryEntity();
        category.setId(20L);
        category.setBookId(1L);
        category.setCategoryType("EXPENSE");
        category.setHiddenFlag(0);
        category.setDeletedFlag(0);
        return category;
    }

    private BookEntity activeBook() {
        BookEntity book = new BookEntity();
        book.setId(1L);
        book.setCurrencyCode("CNY");
        book.setStatus(1);
        return book;
    }

    private TransactionEntity effectiveTransaction() {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setId(100L);
        transaction.setTransactionNo("TXN_100");
        transaction.setRequestId("request-1");
        transaction.setBookId(1L);
        transaction.setCreatedUserId(7L);
        transaction.setTransactionType("EXPENSE");
        transaction.setCategoryId(20L);
        transaction.setAmount(new BigDecimal("30.00"));
        transaction.setCurrencyCode("CNY");
        transaction.setHappenedAt(LocalDateTime.of(2026, 7, 21, 10, 0));
        transaction.setTitle("午餐");
        transaction.setStatus("EFFECTIVE");
        transaction.setVersion(0);
        return transaction;
    }

    private AccountEntryEntity originalExpenseEntry() {
        AccountEntryEntity entry = new AccountEntryEntity();
        entry.setId(501L);
        entry.setTransactionId(100L);
        entry.setAccountId(10L);
        entry.setEntryType("DECREASE");
        entry.setSignedAmount(new BigDecimal("-30.00"));
        entry.setBalanceBefore(new BigDecimal("100.00"));
        entry.setBalanceAfter(new BigDecimal("70.00"));
        return entry;
    }
}
