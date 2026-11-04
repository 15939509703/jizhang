package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.user.dto.SavingsContributionInDTO;
import com.lhj.jizhang.user.dto.SavingsGoalOutDTO;
import com.lhj.jizhang.user.dto.SavingsGoalUpdateInDTO;
import com.lhj.jizhang.user.entity.AccountEntity;
import com.lhj.jizhang.user.entity.SavingsContributionEntity;
import com.lhj.jizhang.user.entity.SavingsGoalEntity;
import com.lhj.jizhang.user.entity.TransactionEntity;
import com.lhj.jizhang.user.mapper.AccountMapper;
import com.lhj.jizhang.user.mapper.SavingsContributionMapper;
import com.lhj.jizhang.user.mapper.SavingsGoalMapper;
import com.lhj.jizhang.user.mapper.TransactionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SavingsGoalServiceTest {
    @Mock
    private SavingsGoalMapper goalMapper;
    @Mock
    private SavingsContributionMapper contributionMapper;
    @Mock
    private AccountMapper accountMapper;
    @Mock
    private TransactionService transactionService;
    @Mock
    private TransactionMapper transactionMapper;
    @Mock
    private AccountService accountService;
    @Mock
    private BookAccessService bookAccessService;

    private SavingsGoalService service;

    @BeforeEach
    void setUp() {
        service = new SavingsGoalService(goalMapper, contributionMapper, accountMapper,
                transactionService, transactionMapper, accountService, bookAccessService);
    }

    @Test
    void shouldUpdateGoalAndRefreshCompletionStatus() {
        SavingsGoalEntity goal = goal();
        when(goalMapper.selectByIdForUpdate(10L)).thenReturn(goal);
        when(contributionMapper.sumEffective(10L)).thenReturn(new BigDecimal("40.00"));
        when(contributionMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        SavingsGoalUpdateInDTO input = new SavingsGoalUpdateInDTO("  旅行基金  ", "  海岛旅行  ",
                new BigDecimal("50.00"), null, LocalDate.parse("2026-08-01"),
                LocalDate.parse("2026-12-31"), 2);

        SavingsGoalOutDTO result = service.update(7L, 10L, input);

        assertEquals("旅行基金", result.name());
        assertEquals("海岛旅行", result.description());
        assertEquals("COMPLETED", result.status());
        assertEquals(3, result.version());
        verify(bookAccessService).requireWritable(7L, 1L);
        verify(goalMapper).updateById(goal);
    }

    @Test
    void shouldRejectStaleGoalUpdate() {
        SavingsGoalEntity goal = goal();
        when(goalMapper.selectByIdForUpdate(10L)).thenReturn(goal);
        SavingsGoalUpdateInDTO input = new SavingsGoalUpdateInDTO("旅行基金", null,
                new BigDecimal("1000.00"), null, LocalDate.parse("2026-08-01"), null, 1);

        assertThrows(BusinessException.class, () -> service.update(7L, 10L, input));

        verify(goalMapper, never()).updateById(goal);
    }

    @Test
    void shouldSoftDeleteGoalWithoutRemovingContributions() {
        SavingsGoalEntity goal = goal();
        when(goalMapper.selectByIdForUpdate(10L)).thenReturn(goal);

        service.delete(7L, 10L);

        assertEquals(1, goal.getDeletedFlag());
        assertEquals("CLOSED", goal.getStatus());
        assertEquals(3, goal.getVersion());
        verify(bookAccessService).requireWritable(7L, 1L);
        verify(goalMapper).updateById(goal);
    }

    @Test
    void shouldReadLinkedAccountBalanceInsteadOfContributionTotal() {
        SavingsGoalEntity goal = goal();
        goal.setTargetAccountId(20L);
        AccountEntity account = new AccountEntity();
        account.setCurrentBalance(new BigDecimal("1200.00"));
        when(goalMapper.selectById(10L)).thenReturn(goal);
        when(accountMapper.selectById(20L)).thenReturn(account);
        when(contributionMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        SavingsGoalOutDTO result = service.get(7L, 10L);

        assertEquals(new BigDecimal("1200.00"), result.completedAmount());
        assertEquals("COMPLETED", result.status());
        verify(contributionMapper, never()).sumEffective(10L);

        account.setCurrentBalance(new BigDecimal("300.00"));
        result = service.get(7L, 10L);
        assertEquals(new BigDecimal("300.00"), result.completedAmount());
        assertEquals("ACTIVE", result.status());
    }

    @Test
    void shouldDepositIntoLinkedAccountAndRecordAdjustment() {
        SavingsGoalEntity goal = goal();
        goal.setTargetAccountId(20L);
        AccountEntity account = new AccountEntity();
        account.setBookId(1L);
        account.setAccountNature("ASSET");
        account.setStatus(1);
        account.setDeletedFlag(0);
        account.setCurrentBalance(new BigDecimal("80.00"));
        TransactionEntity transaction = new TransactionEntity();
        transaction.setId(30L);
        when(goalMapper.selectByIdForUpdate(10L)).thenReturn(goal);
        when(accountMapper.selectById(20L)).thenReturn(account);
        when(transactionMapper.selectOne(any(Wrapper.class))).thenReturn(null, transaction);
        when(contributionMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        SavingsContributionInDTO input = new SavingsContributionInDTO("test", "MANUAL",
                new BigDecimal("20.00"), LocalDate.parse("2026-11-04"), null, null, null);

        service.addContribution(7L, 10L, input);

        verify(accountService).deposit(7L, 20L, new BigDecimal("20.00"), "GOAL_10_test", "存入目标：存款");
        ArgumentCaptor<SavingsContributionEntity> captor = ArgumentCaptor.forClass(SavingsContributionEntity.class);
        verify(contributionMapper).insert(captor.capture());
        assertEquals(30L, captor.getValue().getTransactionId());
    }

    @Test
    void shouldReverseManualDepositWhenContributionIsRemoved() {
        SavingsGoalEntity goal = goal();
        goal.setTargetAccountId(20L);
        SavingsContributionEntity contribution = new SavingsContributionEntity();
        contribution.setGoalId(10L);
        contribution.setTransactionId(30L);
        contribution.setContributionType("MANUAL");
        contribution.setStatus("EFFECTIVE");
        AccountEntity account = new AccountEntity();
        account.setCurrentBalance(new BigDecimal("80.00"));
        when(goalMapper.selectByIdForUpdate(10L)).thenReturn(goal);
        when(contributionMapper.selectById(40L)).thenReturn(contribution);
        when(accountMapper.selectById(20L)).thenReturn(account);
        when(contributionMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        service.removeManualContribution(7L, 10L, 40L);

        verify(transactionService).voidTransaction(7L, 30L);
        verify(contributionMapper, never()).updateById(contribution);
    }

    private SavingsGoalEntity goal() {
        SavingsGoalEntity goal = new SavingsGoalEntity();
        goal.setId(10L);
        goal.setGoalNo("SVG_TEST");
        goal.setBookId(1L);
        goal.setName("存款");
        goal.setTargetAmount(new BigDecimal("1000.00"));
        goal.setInitialAmount(new BigDecimal("10.00"));
        goal.setStartDate(LocalDate.parse("2026-08-01"));
        goal.setStatus("ACTIVE");
        goal.setVersion(2);
        goal.setDeletedFlag(0);
        return goal;
    }
}
