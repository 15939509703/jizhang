package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.user.dto.LoanCreateInDTO;
import com.lhj.jizhang.user.dto.LoanUpdateInDTO;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoanServiceTest {
    private final LoanRecordMapper loanRecordMapper = mock(LoanRecordMapper.class);
    private final BookAccessService bookAccessService = mock(BookAccessService.class);
    private final AccountMapper accountMapper = mock(AccountMapper.class);
    private final AccountEntryMapper accountEntryMapper = mock(AccountEntryMapper.class);
    private final BookMapper bookMapper = mock(BookMapper.class);
    private final TransactionMapper transactionMapper = mock(TransactionMapper.class);
    private LoanService service;

    @BeforeEach
    void setUp() {
        service = new LoanService(loanRecordMapper, bookAccessService, accountMapper,
                accountEntryMapper, bookMapper, transactionMapper);
    }

    @Test
    void lendDecreasesSelectedAccountAndLinksTransaction() {
        AccountEntity account = account();
        BookEntity book = new BookEntity();
        book.setStatus(1);
        book.setCurrencyCode("CNY");
        when(accountMapper.selectByIdForUpdate(20L)).thenReturn(account);
        when(bookMapper.selectById(1L)).thenReturn(book);
        doAnswer(invocation -> {
            invocation.<TransactionEntity>getArgument(0).setId(30L);
            return 1;
        }).when(transactionMapper).insert(any(TransactionEntity.class));

        var result = service.create(7L, input("LEND", 20L));

        ArgumentCaptor<TransactionEntity> transaction = ArgumentCaptor.forClass(TransactionEntity.class);
        ArgumentCaptor<AccountEntryEntity> entry = ArgumentCaptor.forClass(AccountEntryEntity.class);
        verify(transactionMapper).insert(transaction.capture());
        verify(accountEntryMapper).insert(entry.capture());
        verify(accountMapper).updateById(account);
        assertEquals("LEND", transaction.getValue().getTransactionType());
        assertEquals("CNY", transaction.getValue().getCurrencyCode());
        assertEquals(30L, result.relatedTransactionId());
        assertEquals(20L, result.accountId());
        assertEquals(20L, entry.getValue().getAccountId());
        assertEquals(30L, entry.getValue().getTransactionId());
        assertEquals(new BigDecimal("-25.00"), entry.getValue().getSignedAmount());
        assertEquals(new BigDecimal("100.00"), entry.getValue().getBalanceBefore());
        assertEquals(new BigDecimal("75.00"), account.getCurrentBalance());
        assertEquals(1, account.getVersion());
    }

    @Test
    void borrowDoesNotChangeAccount() {
        var result = service.create(7L, input("BORROW", null));

        assertNull(result.relatedTransactionId());
        verify(accountMapper, never()).selectByIdForUpdate(any());
        verify(transactionMapper, never()).insert(any(TransactionEntity.class));
    }

    @Test
    void lendRequiresAccount() {
        assertThrows(BusinessException.class, () -> service.create(7L, input("LEND", null)));
        verify(loanRecordMapper, never()).insert(any(LoanRecordEntity.class));
    }

    @Test
    void lendRejectsAccountFromAnotherBook() {
        AccountEntity account = account();
        account.setBookId(2L);
        when(accountMapper.selectByIdForUpdate(20L)).thenReturn(account);

        assertThrows(BusinessException.class, () -> service.create(7L, input("LEND", 20L)));
        verify(transactionMapper, never()).insert(any(TransactionEntity.class));
    }

    @Test
    void historicalLendDeductsFullAmountWhenAccountIsAdded() {
        LoanRecordEntity loan = loan(null);
        when(loanRecordMapper.selectByIdForUpdate(10L)).thenReturn(loan);
        when(accountMapper.selectByIdForUpdate(20L)).thenReturn(account());
        when(bookMapper.selectById(1L)).thenReturn(book());
        doAnswer(invocation -> {
            invocation.<TransactionEntity>getArgument(0).setId(30L);
            return 1;
        }).when(transactionMapper).insert(any(TransactionEntity.class));

        var result = service.update(7L, 10L, update("25.00", 20L));

        assertEquals(20L, result.accountId());
        assertEquals(30L, result.relatedTransactionId());
        ArgumentCaptor<AccountEntryEntity> entry = ArgumentCaptor.forClass(AccountEntryEntity.class);
        verify(accountEntryMapper).insert(entry.capture());
        assertEquals(new BigDecimal("-25.00"), entry.getValue().getSignedAmount());
        assertEquals(new BigDecimal("75.00"), entry.getValue().getBalanceAfter());
    }

    @Test
    void changingLendAmountReversesOldDebitAndAppliesNewDebit() {
        LoanRecordEntity loan = loan(20L);
        AccountEntity account = account();
        when(loanRecordMapper.selectByIdForUpdate(10L)).thenReturn(loan);
        when(transactionMapper.selectByIdForUpdate(30L)).thenReturn(transaction());
        when(accountMapper.selectByIdForUpdate(20L)).thenReturn(account);

        var result = service.update(7L, 10L, update("30.00", 20L));

        ArgumentCaptor<AccountEntryEntity> entries = ArgumentCaptor.forClass(AccountEntryEntity.class);
        verify(accountEntryMapper, times(2)).insert(entries.capture());
        assertEquals("REVERSAL", entries.getAllValues().get(0).getEntryType());
        assertEquals(new BigDecimal("25.00"), entries.getAllValues().get(0).getSignedAmount());
        assertEquals(new BigDecimal("-30.00"), entries.getAllValues().get(1).getSignedAmount());
        assertEquals(new BigDecimal("95.00"), account.getCurrentBalance());
        assertEquals(new BigDecimal("30.00"), result.totalAmount());
        verify(transactionMapper).updateById(any(TransactionEntity.class));
    }

    @Test
    void changingLendAccountReturnsOldFundsAndDebitsNewAccount() {
        LoanRecordEntity loan = loan(20L);
        AccountEntity previous = account();
        AccountEntity target = account();
        target.setId(21L);
        target.setCurrentBalance(new BigDecimal("50.00"));
        when(loanRecordMapper.selectByIdForUpdate(10L)).thenReturn(loan);
        when(transactionMapper.selectByIdForUpdate(30L)).thenReturn(transaction());
        when(accountMapper.selectByIdForUpdate(20L)).thenReturn(previous);
        when(accountMapper.selectByIdForUpdate(21L)).thenReturn(target);

        var result = service.update(7L, 10L, update("25.00", 21L));

        assertEquals(21L, result.accountId());
        assertEquals(new BigDecimal("125.00"), previous.getCurrentBalance());
        assertEquals(new BigDecimal("25.00"), target.getCurrentBalance());
    }

    @Test
    void editingLendWithoutMoneyChangeLeavesAccountUntouched() {
        LoanRecordEntity loan = loan(20L);
        TransactionEntity transaction = transaction();
        transaction.setTitle("借出给张三");
        when(loanRecordMapper.selectByIdForUpdate(10L)).thenReturn(loan);
        when(transactionMapper.selectByIdForUpdate(30L)).thenReturn(transaction);

        service.update(7L, 10L, update("25.00", 20L));

        verify(accountMapper, never()).updateById(any(AccountEntity.class));
        verify(accountEntryMapper, never()).insert(any(AccountEntryEntity.class));
        verify(transactionMapper, never()).updateById(any(TransactionEntity.class));
    }

    @Test
    void cannotReduceAmountBelowAlreadyRepaidAmount() {
        LoanRecordEntity loan = loan(20L);
        loan.setRepaidAmount(new BigDecimal("20.00"));
        when(loanRecordMapper.selectByIdForUpdate(10L)).thenReturn(loan);

        assertThrows(BusinessException.class, () -> service.update(7L, 10L, update("15.00", 20L)));

        verify(accountMapper, never()).selectByIdForUpdate(any());
        verify(accountEntryMapper, never()).insert(any(AccountEntryEntity.class));
    }

    @Test
    void rejectedAccountDoesNotWriteLedger() {
        LoanRecordEntity loan = loan(null);
        when(loanRecordMapper.selectByIdForUpdate(10L)).thenReturn(loan);

        assertThrows(BusinessException.class, () -> service.update(7L, 10L, update("25.00", 20L)));

        verify(accountEntryMapper, never()).insert(any(AccountEntryEntity.class));
        verify(loanRecordMapper, never()).updateById(any(LoanRecordEntity.class));
    }

    @Test
    void onlyReturnsActiveAccountsFromBook() {
        AccountEntity account = account();
        account.setName("现金");
        when(accountMapper.selectList(any(Wrapper.class))).thenReturn(java.util.List.of(account));

        var accounts = service.accounts(7L, 1L);

        assertEquals(20L, accounts.getFirst().id());
        assertEquals("现金", accounts.getFirst().name());
    }

    private LoanCreateInDTO input(String type, Long accountId) {
        return new LoanCreateInDTO(1L, type, "张三", new BigDecimal("25.00"), accountId, null, null);
    }

    private LoanUpdateInDTO update(String amount, Long accountId) {
        return new LoanUpdateInDTO("张三", new BigDecimal(amount), accountId, null, "OPEN", null, 0);
    }

    private LoanRecordEntity loan(Long accountId) {
        LoanRecordEntity loan = new LoanRecordEntity();
        loan.setBookId(1L);
        loan.setLoanType("LEND");
        loan.setVersion(0);
        loan.setAccountId(accountId);
        loan.setRelatedTransactionId(accountId == null ? null : 30L);
        loan.setTotalAmount(new BigDecimal("25.00"));
        loan.setRepaidAmount(BigDecimal.ZERO);
        return loan;
    }

    private BookEntity book() {
        BookEntity book = new BookEntity();
        book.setStatus(1);
        book.setCurrencyCode("CNY");
        return book;
    }

    private TransactionEntity transaction() {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setId(30L);
        transaction.setBookId(1L);
        transaction.setTransactionType("LEND");
        transaction.setStatus("EFFECTIVE");
        transaction.setVersion(0);
        return transaction;
    }

    private AccountEntity account() {
        AccountEntity account = new AccountEntity();
        account.setId(20L);
        account.setBookId(1L);
        account.setName("现金");
        account.setStatus(1);
        account.setDeletedFlag(0);
        account.setCurrentBalance(new BigDecimal("100.00"));
        account.setVersion(0);
        return account;
    }
}
