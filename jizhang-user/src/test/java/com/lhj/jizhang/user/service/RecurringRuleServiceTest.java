package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lhj.jizhang.user.dto.RecurringRuleCreateInDTO;
import com.lhj.jizhang.user.dto.RecurringRuleOutDTO;
import com.lhj.jizhang.user.entity.AccountEntity;
import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.entity.RecurringRuleEntity;
import com.lhj.jizhang.user.mapper.AccountMapper;
import com.lhj.jizhang.user.mapper.BookMapper;
import com.lhj.jizhang.user.mapper.CategoryMapper;
import com.lhj.jizhang.user.mapper.RecurringExecutionMapper;
import com.lhj.jizhang.user.mapper.RecurringRuleMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecurringRuleServiceTest {
    /** 验证每日与年度规则可创建并保留执行时间和月份。 */
    @Test
    void shouldCreateDailyAndYearlyRules() {
        AccountMapper accountMapper = mock(AccountMapper.class);
        BookMapper bookMapper = mock(BookMapper.class);
        AccountEntity account = new AccountEntity();
        account.setBookId(1L);
        account.setStatus(1);
        account.setDeletedFlag(0);
        when(accountMapper.selectById(any())).thenReturn(account);
        when(bookMapper.selectById(1L)).thenReturn(book());
        RecurringRuleService service = new RecurringRuleService(mock(RecurringRuleMapper.class),
                mock(RecurringExecutionMapper.class), accountMapper, mock(CategoryMapper.class),
                bookMapper, mock(BookAccessService.class));
        LocalDate start = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        RecurringRuleOutDTO daily = service.create(7L, new RecurringRuleCreateInDTO(1L, "TRANSFER", null,
                3L, 4L, BigDecimal.ONE, "每日转账", null, "DAILY", null, null, false,
                LocalTime.of(9, 30), start, null, "AUTO"));
        RecurringRuleOutDTO yearly = service.create(7L, new RecurringRuleCreateInDTO(1L, "TRANSFER", null,
                3L, 4L, BigDecimal.ONE, "年度转账", null, "YEARLY", 2, 29, false,
                LocalTime.of(10, 15), start, null, "CONFIRM"));

        assertEquals("DAILY", daily.recurrenceType());
        assertEquals(LocalTime.of(9, 30), daily.executionTime());
        assertTrue(daily.nextExecutionDate() != null);
        assertEquals("YEARLY", yearly.recurrenceType());
        assertEquals(2, yearly.executionMonth());
        assertEquals(29, yearly.executionDay());
        assertTrue(yearly.nextExecutionDate() != null);
    }

    /** 无效年度日期在读取分类或账户前被拒绝。 */
    @Test
    void shouldRejectNonexistentAnnualDate() {
        BookMapper bookMapper = mock(BookMapper.class);
        when(bookMapper.selectById(1L)).thenReturn(book());
        RecurringRuleService service = new RecurringRuleService(mock(RecurringRuleMapper.class),
                mock(RecurringExecutionMapper.class), mock(AccountMapper.class), mock(CategoryMapper.class),
                bookMapper, mock(BookAccessService.class));
        RecurringRuleCreateInDTO input = new RecurringRuleCreateInDTO(1L, "EXPENSE", 2L, 3L, null,
                BigDecimal.ONE, "年费", null, "YEARLY", 4, 31, false, LocalTime.of(8, 0),
                LocalDate.of(2026, 1, 1), null, "AUTO");
        assertThrows(com.lhj.jizhang.common.exception.BusinessException.class, () -> service.create(7L, input));
    }

    @Test
    void shouldResumeFromNextOccurrenceWithoutBackfillingToday() {
        RecurringRuleMapper ruleMapper = mock(RecurringRuleMapper.class);
        RecurringExecutionMapper executionMapper = mock(RecurringExecutionMapper.class);
        BookMapper bookMapper = mock(BookMapper.class);
        BookAccessService accessService = mock(BookAccessService.class);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        RecurringRuleEntity rule = pausedRule(today);
        when(ruleMapper.selectByIdForUpdate(10L)).thenReturn(rule);
        when(ruleMapper.selectById(10L)).thenReturn(rule);
        when(executionMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(bookMapper.selectById(1L)).thenReturn(book());
        RecurringRuleService service = new RecurringRuleService(ruleMapper, executionMapper,
                mock(AccountMapper.class), mock(CategoryMapper.class), bookMapper, accessService);

        RecurringRuleOutDTO result = service.resume(7L, 10L);

        verify(accessService).requireAction(7L, 1L, com.lhj.jizhang.user.model.BookAction.RECURRING_MANAGE);
        verify(ruleMapper).updateById(rule);
        assertTrue(result.nextExecutionDate().isAfter(today));
        assertEquals(YearMonth.from(today).plusMonths(1), YearMonth.from(result.nextExecutionDate()));
        assertEquals("ACTIVE", result.status());
    }

    private RecurringRuleEntity pausedRule(LocalDate today) {
        RecurringRuleEntity rule = new RecurringRuleEntity();
        rule.setId(10L);
        rule.setRuleNo("RCR_TEST");
        rule.setBookId(1L);
        rule.setCreatedUserId(7L);
        rule.setTransactionType("EXPENSE");
        rule.setRecurrenceType("MONTHLY");
        rule.setExecutionDay(today.getDayOfMonth());
        rule.setMonthEndFlag(0);
        rule.setExecutionTime(LocalTime.of(8, 0));
        rule.setStartDate(today.minusMonths(2));
        rule.setExecutionMode("CONFIRM");
        rule.setStatus("PAUSED");
        rule.setVersion(0);
        rule.setDeletedFlag(0);
        return rule;
    }

    private BookEntity book() {
        BookEntity book = new BookEntity();
        book.setId(1L);
        book.setTimezone("Asia/Shanghai");
        book.setStatus(1);
        return book;
    }
}
