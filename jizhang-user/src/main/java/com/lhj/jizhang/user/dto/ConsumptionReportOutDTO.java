package com.lhj.jizhang.user.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 月度消费总结。
 *
 * @param categoryComparisons 本月和上月出现过的各支出分类环比结果
 */
public record ConsumptionReportOutDTO(
        Long bookId, String month, String currencyCode,
        BigDecimal incomeAmount, BigDecimal expenseAmount, BigDecimal balanceAmount,
        BigDecimal budgetUsageRate, ConsumptionComparisonOutDTO monthOverMonth,
        ConsumptionComparisonOutDTO yearOverYear, BigDecimal maxExpense,
        Integer expenseDays, BigDecimal dailyAverage, Long topCategoryId,
        String topCategoryName, Integer topCategoryCount,
        List<ConsumptionCategoryComparisonOutDTO> categoryComparisons
) { }
