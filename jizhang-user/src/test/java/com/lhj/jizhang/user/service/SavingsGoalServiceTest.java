package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.user.dto.SavingsGoalOutDTO;
import com.lhj.jizhang.user.dto.SavingsGoalUpdateInDTO;
import com.lhj.jizhang.user.entity.SavingsGoalEntity;
import com.lhj.jizhang.user.mapper.AccountMapper;
import com.lhj.jizhang.user.mapper.SavingsContributionMapper;
import com.lhj.jizhang.user.mapper.SavingsGoalMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
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
    private BookAccessService bookAccessService;

    private SavingsGoalService service;

    @BeforeEach
    void setUp() {
        service = new SavingsGoalService(goalMapper, contributionMapper, accountMapper,
                transactionService, bookAccessService);
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
