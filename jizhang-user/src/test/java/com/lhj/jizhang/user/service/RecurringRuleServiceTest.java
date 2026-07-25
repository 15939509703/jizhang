package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lhj.jizhang.user.dto.RecurringRuleOutDTO;
import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.entity.RecurringRuleEntity;
import com.lhj.jizhang.user.mapper.AccountMapper;
import com.lhj.jizhang.user.mapper.BookMapper;
import com.lhj.jizhang.user.mapper.CategoryMapper;
import com.lhj.jizhang.user.mapper.RecurringExecutionMapper;
import com.lhj.jizhang.user.mapper.RecurringRuleMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecurringRuleServiceTest {
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

        verify(accessService).requireWritable(7L, 1L);
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
