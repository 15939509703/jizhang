package com.lhj.jizhang.user.service;

import com.lhj.jizhang.user.dto.BudgetOutDTO;
import com.lhj.jizhang.user.dto.ConsumptionReportOutDTO;
import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.mapper.BookMapper;
import com.lhj.jizhang.user.mapper.ConsumptionReportMapper;
import com.lhj.jizhang.user.mapper.StatisticsMapper;
import com.lhj.jizhang.user.mapper.TransactionMapper;
import com.lhj.jizhang.user.model.StatisticsBreakdownAggregate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsumptionReportServiceTest {
    @Mock private TransactionMapper transactionMapper;
    @Mock private ConsumptionReportMapper reportMapper;
    @Mock private StatisticsMapper statisticsMapper;
    @Mock private BookMapper bookMapper;
    @Mock private BookAccessService accessService;
    @Mock private BudgetService budgetService;
    @InjectMocks private ConsumptionReportService service;

    /** 验证跨年月份的分类支出增长、新增和上月独有分类。 */
    @Test
    void shouldCompareAllExpenseCategoriesAcrossYearBoundary() {
        BookEntity book = new BookEntity();
        book.setStatus(1);
        book.setTimezone("Asia/Shanghai");
        book.setCurrencyCode("CNY");
        when(bookMapper.selectById(1L)).thenReturn(book);
        when(budgetService.getBudget(7L, 1L, YearMonth.of(2026, 1))).thenReturn(new BudgetOutDTO(
                null, null, null, null, null, null, null, null, BigDecimal.ZERO, null,
                null, null, null, null, null, null, null, null, null, null, null));
        when(statisticsMapper.selectCategoryBreakdown(eq(1L), eq("EXPENSE"),
                eq(LocalDateTime.of(2025, 12, 31, 16, 0)), eq(LocalDateTime.of(2026, 1, 31, 16, 0))))
                .thenReturn(List.of(category(10L, "餐饮", "1100.00"), category(20L, "交通", "80.00")));
        when(statisticsMapper.selectCategoryBreakdown(eq(1L), eq("EXPENSE"),
                eq(LocalDateTime.of(2025, 11, 30, 16, 0)), eq(LocalDateTime.of(2025, 12, 31, 16, 0))))
                .thenReturn(List.of(category(10L, "餐饮", "1000.00"), category(30L, "住房", "500.00")));

        ConsumptionReportOutDTO report = service.get(7L, 1L, YearMonth.of(2026, 1));

        verify(accessService).requireMember(7L, 1L);
        assertEquals(3, report.categoryComparisons().size());
        assertEquals("餐饮", report.categoryComparisons().get(0).categoryName());
        assertEquals(new BigDecimal("100.00"), report.categoryComparisons().get(0).monthOverMonth().changeAmount());
        assertEquals(new BigDecimal("0.1000"), report.categoryComparisons().get(0).monthOverMonth().changeRate());
        assertEquals(new BigDecimal("80.00"), report.categoryComparisons().get(1).monthOverMonth().changeAmount());
        assertNull(report.categoryComparisons().get(1).monthOverMonth().changeRate());
        assertEquals(BigDecimal.ZERO, report.categoryComparisons().get(2).expenseAmount());
        assertEquals(new BigDecimal("-500.00"), report.categoryComparisons().get(2).monthOverMonth().changeAmount());
        assertEquals(new BigDecimal("-1.0000"), report.categoryComparisons().get(2).monthOverMonth().changeRate());
    }

    /** 构造分类支出聚合数据。 */
    private StatisticsBreakdownAggregate category(Long id, String name, String amount) {
        StatisticsBreakdownAggregate row = new StatisticsBreakdownAggregate();
        row.setItemId(id);
        row.setItemName(name);
        row.setExpenseAmount(new BigDecimal(amount));
        return row;
    }
}
