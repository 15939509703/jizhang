package com.lhj.jizhang.user.service;

import com.lhj.jizhang.user.dto.BillOverviewOutDTO;
import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.mapper.BookMapper;
import com.lhj.jizhang.user.mapper.StatisticsMapper;
import com.lhj.jizhang.user.mapper.TransactionMapper;
import com.lhj.jizhang.user.model.StatisticsPeriodAggregate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {
    @Mock private StatisticsMapper statisticsMapper;
    @Mock private TransactionMapper transactionMapper;
    @Mock private BookMapper bookMapper;
    @Mock private BookAccessService bookAccessService;
    @InjectMocks private StatisticsService service;

    /** 验证历年收入支出和选中年份月度金额使用同一账本及自然年口径。 */
    @Test
    void shouldReturnYearAndMonthlyBillOverview() {
        BookEntity book = new BookEntity();
        book.setStatus(1);
        book.setTimezone("Asia/Shanghai");
        book.setCurrencyCode("CNY");
        when(bookMapper.selectById(1L)).thenReturn(book);
        when(statisticsMapper.selectYearly(1L, "Asia/Shanghai", "+08:00"))
                .thenReturn(List.of(period("2026", "100.00", "35.00"), period("2025", "20.00", "80.00")));
        when(statisticsMapper.selectMonthly(eq(1L), eq(LocalDateTime.of(2025, 12, 31, 16, 0)),
                eq(LocalDateTime.of(2026, 12, 31, 16, 0)), eq("+08:00")))
                .thenReturn(List.of(period("2026-01", "100.00", "35.00")));

        BillOverviewOutDTO overview = service.billOverview(7L, 1L, 2026);

        verify(bookAccessService).requireMember(7L, 1L);
        assertEquals("CNY", overview.currencyCode());
        assertEquals(2026, overview.year());
        assertEquals(new BigDecimal("100.00"), overview.years().get(0).income());
        assertEquals(new BigDecimal("80.00"), overview.years().get(1).expense());
        assertEquals(12, overview.months().size());
        assertEquals(new BigDecimal("35.00"), overview.months().get(0).expense());
        assertEquals(BigDecimal.ZERO, overview.months().get(1).income());
    }

    /** 构造数据库返回的期间汇总。 */
    private StatisticsPeriodAggregate period(String key, String income, String expense) {
        StatisticsPeriodAggregate row = new StatisticsPeriodAggregate();
        row.setPeriodKey(key);
        row.setIncomeAmount(new BigDecimal(income));
        row.setExpenseAmount(new BigDecimal(expense));
        return row;
    }
}
