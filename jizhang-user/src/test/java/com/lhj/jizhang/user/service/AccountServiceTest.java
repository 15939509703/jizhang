package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.user.dto.AccountCreateInDTO;
import com.lhj.jizhang.user.dto.AccountUpdateInDTO;
import com.lhj.jizhang.user.dto.AccountOutDTO;
import com.lhj.jizhang.user.entity.AccountEntity;
import com.lhj.jizhang.user.entity.AccountEntryEntity;
import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.entity.TransactionEntity;
import com.lhj.jizhang.user.mapper.AccountEntryMapper;
import com.lhj.jizhang.user.mapper.AccountMapper;
import com.lhj.jizhang.user.mapper.BookMapper;
import com.lhj.jizhang.user.mapper.TransactionMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountServiceTest {
    @Test
    void shouldCreateAccountAtEndOfCustomOrder() {
        AccountMapper accountMapper = mock(AccountMapper.class);
        BookAccessService bookAccessService = mock(BookAccessService.class);
        AccountEntity last = account();
        last.setSortNo(40);
        when(accountMapper.selectOne(any(Wrapper.class))).thenReturn(last);
        doAnswer(invocation -> {
            invocation.<AccountEntity>getArgument(0).setId(30L);
            return 1;
        }).when(accountMapper).insert(any(AccountEntity.class));
        AccountService service = service(accountMapper, bookAccessService);

        AccountOutDTO result = service.create(7L,
                new AccountCreateInDTO(1L, "工资卡", "BANK", "ASSET", BigDecimal.ZERO, true));

        assertEquals(50, result.sortNo());
        verify(bookAccessService).requireWritable(7L, 1L);
    }

    @Test
    void shouldRejectNegativeCreditLimit() {
        AccountMapper accountMapper = mock(AccountMapper.class);
        BookAccessService bookAccessService = mock(BookAccessService.class);
        AccountService service = service(accountMapper, bookAccessService);

        assertThrows(BusinessException.class, () -> service.create(7L,
                new AccountCreateInDTO(1L, "信用卡", "CREDIT", "LIABILITY",
                        new BigDecimal("-1.00"), true)));
    }

    @Test
    void shouldUpdateAccountNameAfterWritableCheck() {
        AccountMapper accountMapper = mock(AccountMapper.class);
        BookAccessService bookAccessService = mock(BookAccessService.class);
        AccountEntity account = account();
        when(accountMapper.selectById(20L)).thenReturn(account);
        AccountService service = service(accountMapper, bookAccessService);

        AccountOutDTO result = service.update(7L, 20L, new AccountUpdateInDTO("  工资卡  "));

        assertEquals("工资卡", result.name());
        verify(bookAccessService).requireWritable(7L, 1L);
        verify(accountMapper).updateById(account);
    }

    @Test
    void shouldSoftDeleteAccountAfterWritableCheck() {
        AccountMapper accountMapper = mock(AccountMapper.class);
        BookAccessService bookAccessService = mock(BookAccessService.class);
        AccountEntity account = account();
        when(accountMapper.selectById(20L)).thenReturn(account);
        AccountService service = service(accountMapper, bookAccessService);

        service.delete(7L, 20L);

        ArgumentCaptor<AccountEntity> captor = ArgumentCaptor.forClass(AccountEntity.class);
        verify(bookAccessService).requireWritable(7L, 1L);
        verify(accountMapper).updateById(captor.capture());
        assertEquals(1, captor.getValue().getDeletedFlag());
        assertEquals(0, captor.getValue().getStatus());
    }

    @Test
    void shouldDepositUsingLockedBalanceAndRecordAdjustment() {
        AccountMapper accountMapper = mock(AccountMapper.class);
        AccountEntryMapper entryMapper = mock(AccountEntryMapper.class);
        TransactionMapper transactionMapper = mock(TransactionMapper.class);
        BookMapper bookMapper = mock(BookMapper.class);
        BookAccessService bookAccessService = mock(BookAccessService.class);
        AccountEntity account = account();
        account.setCurrentBalance(new BigDecimal("100.00"));
        BookEntity book = new BookEntity();
        book.setCurrencyCode("CNY");
        when(accountMapper.selectByIdForUpdate(20L)).thenReturn(account);
        when(bookMapper.selectById(1L)).thenReturn(book);
        AccountService service = new AccountService(accountMapper, entryMapper, transactionMapper,
                bookMapper, bookAccessService);

        AccountOutDTO result = service.deposit(7L, 20L, new BigDecimal("25.00"), "request", "存入目标");

        assertEquals(new BigDecimal("125.00"), result.currentBalance());
        ArgumentCaptor<AccountEntryEntity> entry = ArgumentCaptor.forClass(AccountEntryEntity.class);
        verify(entryMapper).insert(entry.capture());
        assertEquals(new BigDecimal("25.00"), entry.getValue().getSignedAmount());
        ArgumentCaptor<TransactionEntity> transaction = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionMapper).insert(transaction.capture());
        assertEquals("ADJUSTMENT", transaction.getValue().getTransactionType());
    }

    private AccountService service(AccountMapper accountMapper, BookAccessService bookAccessService) {
        return new AccountService(accountMapper, mock(AccountEntryMapper.class), mock(TransactionMapper.class),
                mock(BookMapper.class), bookAccessService);
    }

    private AccountEntity account() {
        AccountEntity account = new AccountEntity();
        account.setId(20L);
        account.setAccountNo("ACC_TEST");
        account.setBookId(1L);
        account.setName("微信零钱");
        account.setAccountType("WECHAT");
        account.setAccountNature("ASSET");
        account.setInitialBalance(BigDecimal.ZERO);
        account.setCurrentBalance(BigDecimal.ZERO);
        account.setIncludedInAssets(1);
        account.setStatus(1);
        account.setVersion(0);
        account.setDeletedFlag(0);
        return account;
    }
}
