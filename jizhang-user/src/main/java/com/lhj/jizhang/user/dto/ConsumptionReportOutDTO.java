package com.lhj.jizhang.user.dto;

import java.math.BigDecimal;

public record ConsumptionReportOutDTO(
        Long bookId, String month, String currencyCode,
        BigDecimal incomeAmount, BigDecimal expenseAmount, BigDecimal balanceAmount,
        BigDecimal budgetUsageRate, ConsumptionComparisonOutDTO monthOverMonth,
        ConsumptionComparisonOutDTO yearOverYear, BigDecimal maxExpense,
        Integer expenseDays, BigDecimal dailyAverage, Long topCategoryId,
        String topCategoryName, Integer topCategoryCount
) { }
