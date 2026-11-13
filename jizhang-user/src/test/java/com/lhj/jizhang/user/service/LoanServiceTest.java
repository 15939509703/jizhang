package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.user.dto.LoanCreateInDTO;
import com.lhj.jizhang.user.dto.BookAccessOutDTO;
import com.lhj.jizhang.user.dto.LoanRepaymentInDTO;
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
import com.lhj.jizhang.user.model.BookAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
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
    void borrowIncreasesSelectedAccountAndLinksTransaction() {
        AccountEntity account = account();
        when(accountMapper.selectByIdForUpdate(20L)).thenReturn(account);
        when(bookMapper.selectById(1L)).thenReturn(book());
        doAnswer(invocation -> {
            invocation.<TransactionEntity>getArgument(0).setId(30L);
            return 1;
        }).when(transactionMapper).insert(any(TransactionEntity.class));

        var result = service.create(7L, input("BORROW", 20L));

        ArgumentCaptor<TransactionEntity> transaction = ArgumentCaptor.forClass(TransactionEntity.class);
        ArgumentCaptor<AccountEntryEntity> entry = ArgumentCaptor.forClass(AccountEntryEntity.class);
        verify(transactionMapper).insert(transaction.capture());
        verify(accountEntryMapper).insert(entry.capture());
        assertEquals("BORROW", transaction.getValue().getTransactionType());
        assertEquals(30L, result.relatedTransactionId());
        assertEquals(20L, result.accountId());
        assertEquals(new BigDecimal("25.00"), entry.getValue().getSignedAmount());
        assertEquals("INCREASE", entry.getValue().getEntryType());
        assertEquals(new BigDecimal("125.00"), account.getCurrentBalance());
    }

    @Test
    void lendRequiresAccount() {
        assertThrows(BusinessException.class, () -> service.create(7L, input("LEND", null)));
        assertThrows(BusinessException.class, () -> service.create(7L, input("BORROW", null)));
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
    void changingBorrowAccountReversesOldCreditAndCreditsNewAccount() {
        LoanRecordEntity loan = loan(20L);
        loan.setLoanType("BORROW");
        AccountEntity previous = account();
        AccountEntity target = account();
        target.setId(21L);
        target.setCurrentBalance(new BigDecimal("50.00"));
        TransactionEntity original = transaction();
        original.setTransactionType("BORROW");
        when(loanRecordMapper.selectByIdForUpdate(10L)).thenReturn(loan);
        when(transactionMapper.selectByIdForUpdate(30L)).thenReturn(original);
        when(accountMapper.selectByIdForUpdate(20L)).thenReturn(previous);
        when(accountMapper.selectByIdForUpdate(21L)).thenReturn(target);

        service.update(7L, 10L, update("25.00", 21L));

        assertEquals(new BigDecimal("75.00"), previous.getCurrentBalance());
        assertEquals(new BigDecimal("75.00"), target.getCurrentBalance());
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
        when(bookAccessService.myAccess(7L, 1L)).thenReturn(new BookAccessOutDTO(1L, "MEMBER",
                Map.of("MENU.LOANS", true), Map.of("ACTION.LOANS.MANAGE", false,
                        "ACTION.LOANS.REPAY", true), "ALL", 0L));
        when(accountMapper.selectList(any(Wrapper.class))).thenReturn(java.util.List.of(account));

        var accounts = service.accounts(7L, 1L);

        assertEquals(20L, accounts.getFirst().id());
        assertEquals("现金", accounts.getFirst().name());
    }

    /** 部分收回只增加本次金额，并生成独立的本金收回流水。 */
    @Test
    void partialLendRepaymentCreditsLinkedAccountAndCreatesRepaymentLedger() {
        LoanRecordEntity loan = repaymentLoan();
        AccountEntity account = account();
        prepareRepayment(loan, account);

        var result = service.repay(7L, 10L, repayment("10.00"));

        assertEquals(new BigDecimal("10.00"), result.repaidAmount());
        assertEquals(new BigDecimal("15.00"), result.remainingAmount());
        assertEquals("PARTIAL", result.status());
        assertEquals(30L, result.relatedTransactionId());
        assertEquals(new BigDecimal("110.00"), account.getCurrentBalance());
        assertEquals(1, account.getVersion());
        assertRepaymentLedger("10.00", "100.00", "110.00");
        verify(loanRecordMapper).updateById(loan);
    }

    /** 分次收回累计至结清时，每次仅回补对应金额。 */
    @Test
    void consecutiveRepaymentsCreditEachAmountAndCloseAtTotal() {
        LoanRecordEntity loan = repaymentLoan();
        AccountEntity account = account();
        prepareRepayment(loan, account);

        service.repay(7L, 10L, repayment("10.00"));
        var result = service.repay(7L, 10L, repayment("15.00"));

        assertEquals(new BigDecimal("25.00"), result.repaidAmount());
        assertEquals(new BigDecimal("0.00"), result.remainingAmount());
        assertEquals("CLOSED", result.status());
        assertEquals(new BigDecimal("125.00"), account.getCurrentBalance());
        assertEquals(2, account.getVersion());
        ArgumentCaptor<AccountEntryEntity> entries = ArgumentCaptor.forClass(AccountEntryEntity.class);
        verify(accountEntryMapper, times(2)).insert(entries.capture());
        assertEquals(new BigDecimal("10.00"), entries.getAllValues().get(0).getSignedAmount());
        assertEquals(new BigDecimal("15.00"), entries.getAllValues().get(1).getSignedAmount());
        assertEquals(new BigDecimal("110.00"), entries.getAllValues().get(1).getBalanceBefore());
    }

    /** 已存在收回金额时，结清不会将历史已收金额重复入账。 */
    @Test
    void finalRepaymentDoesNotCreditPreviouslyCollectedAmountAgain() {
        LoanRecordEntity loan = repaymentLoan();
        loan.setRepaidAmount(new BigDecimal("5.00"));
        AccountEntity account = account();
        prepareRepayment(loan, account);

        var result = service.repay(7L, 10L, repayment("20.00"));

        assertEquals("CLOSED", result.status());
        assertEquals(new BigDecimal("120.00"), account.getCurrentBalance());
        assertRepaymentLedger("20.00", "100.00", "120.00");
    }

    /** 借入还款从关联账户扣除本次本金。 */
    @Test
    void borrowRepaymentDebitsLinkedAccount() {
        LoanRecordEntity loan = repaymentLoan();
        loan.setLoanType("BORROW");
        AccountEntity account = account();
        prepareRepayment(loan, account);

        var result = service.repay(7L, 10L, repayment("10.00"));

        assertEquals(new BigDecimal("10.00"), result.repaidAmount());
        assertEquals("PARTIAL", result.status());
        assertEquals(new BigDecimal("90.00"), account.getCurrentBalance());
        ArgumentCaptor<AccountEntryEntity> entry = ArgumentCaptor.forClass(AccountEntryEntity.class);
        verify(accountEntryMapper).insert(entry.capture());
        assertEquals("DECREASE", entry.getValue().getEntryType());
        assertEquals(new BigDecimal("-10.00"), entry.getValue().getSignedAmount());
    }

    @Test
    void repaymentCanUseAnotherAccountWithoutChangingOriginalAccount() {
        LoanRecordEntity loan = repaymentLoan();
        AccountEntity original = account();
        AccountEntity selected = account();
        selected.setId(21L);
        selected.setCurrentBalance(new BigDecimal("50.00"));
        prepareRepayment(loan, original);
        when(accountMapper.selectByIdForUpdate(21L)).thenReturn(selected);

        service.repay(7L, 10L, new LoanRepaymentInDTO(new BigDecimal("10.00"), 21L, null));

        assertEquals(new BigDecimal("100.00"), original.getCurrentBalance());
        assertEquals(new BigDecimal("60.00"), selected.getCurrentBalance());
        ArgumentCaptor<AccountEntryEntity> entry = ArgumentCaptor.forClass(AccountEntryEntity.class);
        verify(accountEntryMapper).insert(entry.capture());
        assertEquals(21L, entry.getValue().getAccountId());
    }

    /** 超额收回必须在任何资金写入前被拒绝。 */
    @Test
    void overpaymentDoesNotCreditAccountOrUpdateLoan() {
        LoanRecordEntity loan = repaymentLoan();
        loan.setRepaidAmount(new BigDecimal("20.00"));
        when(loanRecordMapper.selectByIdForUpdate(10L)).thenReturn(loan);

        assertThrows(BusinessException.class, () -> service.repay(7L, 10L, repayment("10.00")));

        assertNoRepaymentWrites();
    }

    /** 已结清记录拒绝再次收款，防止重复增加余额。 */
    @Test
    void closedLoanDoesNotAcceptAnotherRepayment() {
        LoanRecordEntity loan = repaymentLoan();
        loan.setStatus("CLOSED");
        loan.setRepaidAmount(new BigDecimal("25.00"));
        when(loanRecordMapper.selectByIdForUpdate(10L)).thenReturn(loan);

        assertThrows(BusinessException.class, () -> service.repay(7L, 10L, repayment("1.00")));

        assertNoRepaymentWrites();
    }

    /** 空金额、零金额和负金额不能产生收回流水。 */
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0.00", "-1.00"})
    void invalidRepaymentAmountDoesNotWriteLedger(String amount) {
        when(loanRecordMapper.selectByIdForUpdate(10L)).thenReturn(repaymentLoan());
        LoanRepaymentInDTO input = new LoanRepaymentInDTO(amount == null ? null : new BigDecimal(amount), null, null);

        assertThrows(BusinessException.class, () -> service.repay(7L, 10L, input));

        assertNoRepaymentWrites();
    }

    /** 未关联账户或原流水的历史借出必须先补齐关联信息。 */
    @ParameterizedTest
    @ValueSource(strings = {"account", "transaction"})
    void incompleteHistoricalLendDoesNotRecordRepayment(String missingField) {
        LoanRecordEntity loan = repaymentLoan();
        if ("account".equals(missingField)) {
            loan.setAccountId(null);
        } else {
            loan.setRelatedTransactionId(null);
        }
        when(loanRecordMapper.selectByIdForUpdate(10L)).thenReturn(loan);

        assertThrows(BusinessException.class, () -> service.repay(7L, 10L, repayment("10.00")));

        assertNoRepaymentWrites();
    }

    /** 原借出流水不存在、失效、类型错误或账本不符时拒绝入账。 */
    @ParameterizedTest
    @ValueSource(strings = {"missing", "voided", "type", "book"})
    void invalidOriginalLendDoesNotRecordRepayment(String problem) {
        TransactionEntity original = transaction();
        if ("voided".equals(problem)) {
            original.setStatus("VOIDED");
        } else if ("type".equals(problem)) {
            original.setTransactionType("INCOME");
        } else if ("book".equals(problem)) {
            original.setBookId(2L);
        }
        when(loanRecordMapper.selectByIdForUpdate(10L)).thenReturn(repaymentLoan());
        when(transactionMapper.selectByIdForUpdate(30L)).thenReturn("missing".equals(problem) ? null : original);

        assertThrows(BusinessException.class, () -> service.repay(7L, 10L, repayment("10.00")));

        assertNoRepaymentWrites();
    }

    /** 收款账户不存在、跨账本、停用或删除时不能只登记收款状态。 */
    @ParameterizedTest
    @ValueSource(strings = {"missing", "book", "disabled", "deleted"})
    void invalidRepaymentAccountDoesNotUpdateLoan(String problem) {
        AccountEntity account = account();
        if ("book".equals(problem)) {
            account.setBookId(2L);
        } else if ("disabled".equals(problem)) {
            account.setStatus(0);
        } else if ("deleted".equals(problem)) {
            account.setDeletedFlag(1);
        }
        prepareRepayment(repaymentLoan(), "missing".equals(problem) ? null : account);

        assertThrows(BusinessException.class, () -> service.repay(7L, 10L, repayment("10.00")));

        assertNoRepaymentWrites();
    }

    /** 编辑为结清时仅回补尚未收回的本金。 */
    @Test
    void editingLendAsClosedCreditsOnlyRemainingAmount() {
        LoanRecordEntity loan = repaymentLoan();
        loan.setRepaidAmount(new BigDecimal("5.00"));
        AccountEntity account = account();
        prepareRepayment(loan, account);
        LoanUpdateInDTO input = new LoanUpdateInDTO("张三", new BigDecimal("25.00"),
                20L, null, "CLOSED", null, 0);

        var result = service.update(7L, 10L, input);

        assertEquals("CLOSED", result.status());
        assertEquals(new BigDecimal("25.00"), result.repaidAmount());
        assertEquals(new BigDecimal("120.00"), account.getCurrentBalance());
        assertRepaymentLedger("20.00", "100.00", "120.00");
    }

    /** 仅有管理权限时，编辑结清仍需收款权限且不能产生资金流水。 */
    @Test
    void closingLendWithoutRepaymentPermissionDoesNotWriteLedger() {
        prepareRepayment(repaymentLoan(), account());
        when(bookAccessService.requireAction(7L, 1L, BookAction.LOANS_REPAY))
                .thenThrow(new BusinessException(ErrorCodes.BOOK_ACCESS_DENIED, "无收款权限"));
        LoanUpdateInDTO input = new LoanUpdateInDTO("张三", new BigDecimal("25.00"),
                20L, null, "CLOSED", null, 0);

        assertThrows(BusinessException.class, () -> service.update(7L, 10L, input));

        verify(bookAccessService).requireAction(7L, 1L, BookAction.LOANS_MANAGE);
        verify(bookAccessService).requireAction(7L, 1L, BookAction.LOANS_REPAY);
        assertNoRepaymentWrites();
    }

    /** 长往来人名称收回时，流水标题保留收款前缀并符合数据库长度限制。 */
    @Test
    void repaymentWithLongCounterpartyNameTruncatesTitleToColumnLimit() {
        LoanRecordEntity loan = repaymentLoan();
        loan.setCounterpartyName("张".repeat(128));
        prepareRepayment(loan, account());

        service.repay(7L, 10L, repayment("10.00"));

        ArgumentCaptor<TransactionEntity> transaction = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionMapper).insert(transaction.capture());
        assertTrue(transaction.getValue().getTitle().startsWith("收回"));
        assertEquals(128, transaction.getValue().getTitle().length());
    }

    /** 重复保存已结清状态不会再次回补本金。 */
    @Test
    void savingClosedLendAgainDoesNotCreditAccountTwice() {
        LoanRecordEntity loan = repaymentLoan();
        AccountEntity account = account();
        prepareRepayment(loan, account);
        service.update(7L, 10L, new LoanUpdateInDTO("张三", new BigDecimal("25.00"),
                20L, null, "CLOSED", null, 0));

        service.update(7L, 10L, new LoanUpdateInDTO("张三", new BigDecimal("25.00"),
                20L, null, "CLOSED", null, 1));

        assertEquals(new BigDecimal("125.00"), account.getCurrentBalance());
        verify(transactionMapper).insert(any(TransactionEntity.class));
        verify(accountEntryMapper).insert(any(AccountEntryEntity.class));
        verify(accountMapper).updateById(account);
    }

    /** 修改借出账户不会搬移已经收回到原账户的独立流水。 */
    @Test
    void changingLendAccountPreservesPreviouslyCreditedRepayment() {
        LoanRecordEntity loan = repaymentLoan();
        AccountEntity previous = account();
        AccountEntity target = account();
        target.setId(21L);
        target.setCurrentBalance(new BigDecimal("50.00"));
        prepareRepayment(loan, previous);
        when(accountMapper.selectByIdForUpdate(21L)).thenReturn(target);
        service.repay(7L, 10L, repayment("10.00"));

        service.update(7L, 10L, new LoanUpdateInDTO("张三", new BigDecimal("25.00"),
                21L, null, "PARTIAL", null, 1));

        assertEquals(new BigDecimal("135.00"), previous.getCurrentBalance());
        assertEquals(new BigDecimal("25.00"), target.getCurrentBalance());
        ArgumentCaptor<AccountEntryEntity> entries = ArgumentCaptor.forClass(AccountEntryEntity.class);
        verify(accountEntryMapper, times(3)).insert(entries.capture());
        assertEquals(40L, entries.getAllValues().getFirst().getTransactionId());
        assertEquals(20L, entries.getAllValues().getFirst().getAccountId());
        assertEquals(new BigDecimal("10.00"), entries.getAllValues().getFirst().getSignedAmount());
        verify(transactionMapper).insert(any(TransactionEntity.class));
    }

    /** 入账分录异常时，事务代理必须回滚而非提交收款交易。 */
    @Test
    void repaymentEntryFailureRollsBackTransaction() {
        prepareRepayment(repaymentLoan(), account());
        doThrow(new IllegalStateException("分录写入失败"))
                .when(accountEntryMapper).insert(any(AccountEntryEntity.class));
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        ProxyFactory factory = new ProxyFactory(service);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        LoanService transactionalService = (LoanService) factory.getProxy();

        assertThrows(IllegalStateException.class,
                () -> transactionalService.repay(7L, 10L, repayment("10.00")));

        verify(transactionMapper).insert(any(TransactionEntity.class));
        verify(manager).rollback(status);
        verify(manager, never()).commit(any());
        verify(loanRecordMapper, never()).updateById(any(LoanRecordEntity.class));
    }

    /** 构造包含完整关联信息的待收回借出记录。 */
    private LoanRecordEntity repaymentLoan() {
        LoanRecordEntity loan = loan(20L);
        loan.setId(10L);
        loan.setCounterpartyName("张三");
        loan.setStatus("OPEN");
        return loan;
    }

    /** 准备已锁定的原借出流水、关联账户及收回流水主键。 */
    private void prepareRepayment(LoanRecordEntity loan, AccountEntity account) {
        TransactionEntity original = transaction();
        original.setTransactionType(loan.getLoanType());
        original.setCurrencyCode("CNY");
        original.setTitle("借出给张三");
        AtomicLong nextTransactionId = new AtomicLong(40L);
        when(loanRecordMapper.selectByIdForUpdate(10L)).thenReturn(loan);
        when(transactionMapper.selectByIdForUpdate(30L)).thenReturn(original);
        when(accountMapper.selectByIdForUpdate(20L)).thenReturn(account);
        doAnswer(invocation -> {
            invocation.<TransactionEntity>getArgument(0).setId(nextTransactionId.getAndIncrement());
            return 1;
        }).when(transactionMapper).insert(any(TransactionEntity.class));
    }

    /** 构造本次收回金额与备注。 */
    private LoanRepaymentInDTO repayment(String amount) {
        return new LoanRepaymentInDTO(new BigDecimal(amount), null, "微信已收款");
    }

    /** 验证本金收回流水及账户分录的关联、金额、币种和前后余额。 */
    private void assertRepaymentLedger(String amount, String balanceBefore, String balanceAfter) {
        ArgumentCaptor<TransactionEntity> transaction = ArgumentCaptor.forClass(TransactionEntity.class);
        ArgumentCaptor<AccountEntryEntity> entry = ArgumentCaptor.forClass(AccountEntryEntity.class);
        verify(transactionMapper).insert(transaction.capture());
        verify(accountEntryMapper).insert(entry.capture());
        assertEquals("REPAYMENT", transaction.getValue().getTransactionType());
        assertEquals(30L, transaction.getValue().getOriginalTransactionId());
        assertEquals("CNY", transaction.getValue().getCurrencyCode());
        assertEquals("EFFECTIVE", transaction.getValue().getStatus());
        assertEquals(new BigDecimal(amount), transaction.getValue().getAmount());
        assertEquals(transaction.getValue().getId(), entry.getValue().getTransactionId());
        assertEquals(20L, entry.getValue().getAccountId());
        assertEquals("INCREASE", entry.getValue().getEntryType());
        assertEquals(new BigDecimal(amount), entry.getValue().getSignedAmount());
        assertEquals(new BigDecimal(balanceBefore), entry.getValue().getBalanceBefore());
        assertEquals(new BigDecimal(balanceAfter), entry.getValue().getBalanceAfter());
    }

    /** 验证失败请求没有新增交易、分录或修改借还记录。 */
    private void assertNoRepaymentWrites() {
        verify(transactionMapper, never()).insert(any(TransactionEntity.class));
        verify(accountEntryMapper, never()).insert(any(AccountEntryEntity.class));
        verify(accountMapper, never()).updateById(any(AccountEntity.class));
        verify(loanRecordMapper, never()).updateById(any(LoanRecordEntity.class));
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
