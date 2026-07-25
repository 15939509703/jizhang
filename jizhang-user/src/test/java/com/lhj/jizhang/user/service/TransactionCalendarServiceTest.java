package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lhj.jizhang.user.dto.TransactionCalendarDayOutDTO;
import com.lhj.jizhang.user.dto.TransactionCalendarOutDTO;
import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.entity.RecurringExecutionEntity;
import com.lhj.jizhang.user.entity.RecurringRuleEntity;
import com.lhj.jizhang.user.entity.TransactionEntity;
import com.lhj.jizhang.user.mapper.BookMapper;
import com.lhj.jizhang.user.mapper.RecurringExecutionMapper;
import com.lhj.jizhang.user.mapper.RecurringRuleMapper;
import com.lhj.jizhang.user.mapper.TransactionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionCalendarServiceTest {
    @Mock
    private TransactionMapper transactionMapper;
    @Mock
    private RecurringRuleMapper ruleMapper;
    @Mock
    private RecurringExecutionMapper executionMapper;
    @Mock
    private BookMapper bookMapper;
    @Mock
    private BookAccessService bookAccessService;

    private TransactionCalendarService service;

    @BeforeEach
    void setUp() {
        service = new TransactionCalendarService(transactionMapper, ruleMapper, executionMapper,
                bookMapper, bookAccessService);
    }

    @Test
    void shouldAggregateByBookTimezoneAndExposeUtcDayBoundaries() {
        when(bookMapper.selectById(1L)).thenReturn(book());
        when(transactionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                transaction("INCOME", "20.00", LocalDateTime.of(2026, 7, 1, 15, 59)),
                transaction("EXPENSE", "7.50", LocalDateTime.of(2026, 7, 1, 16, 0))));
        RecurringRuleEntity rule = new RecurringRuleEntity();
        rule.setId(10L);
        when(ruleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(rule));
        RecurringExecutionEntity pending = new RecurringExecutionEntity();
        pending.setScheduledDate(LocalDate.of(2026, 7, 2));
        when(executionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(pending));

        TransactionCalendarOutDTO result = service.get(7L, 1L, YearMonth.of(2026, 7));

        verify(bookAccessService).requireMember(7L, 1L);
        assertEquals(31, result.days().size());
        assertEquals(LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")), result.today());
        assertEquals(new BigDecimal("20.00"), result.incomeAmount());
        assertEquals(new BigDecimal("7.50"), result.expenseAmount());
        TransactionCalendarDayOutDTO first = result.days().getFirst();
        TransactionCalendarDayOutDTO second = result.days().get(1);
        assertEquals(new BigDecimal("20.00"), first.incomeAmount());
        assertEquals(Instant.parse("2026-06-30T16:00:00Z"), first.startAt());
        assertEquals(Instant.parse("2026-07-01T16:00:00Z"), first.endAt());
        assertEquals(new BigDecimal("7.50"), second.expenseAmount());
        assertEquals(1, second.pendingRecurringCount());
    }

    private BookEntity book() {
        BookEntity book = new BookEntity();
        book.setId(1L);
        book.setTimezone("Asia/Shanghai");
        book.setCurrencyCode("CNY");
        book.setStatus(1);
        return book;
    }

    private TransactionEntity transaction(String type, String amount, LocalDateTime happenedAt) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setTransactionType(type);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setHappenedAt(happenedAt);
        return transaction;
    }
}
