package com.lhj.jizhang.user.dto;

import java.util.List;

public record StatisticsDashboardOutDTO(
        String month,
        String weekStart,
        String weekEnd,
        Integer year,
        String currencyCode,
        StatisticsSummaryOutDTO weekSummary,
        StatisticsSummaryOutDTO monthSummary,
        StatisticsSummaryOutDTO yearSummary,
        StatisticsSummaryOutDTO todaySummary,
        List<StatisticsTrendOutDTO> weeklyTrend,
        List<StatisticsTrendOutDTO> monthlyTrend,
        List<StatisticsTrendOutDTO> trend7Days,
        List<StatisticsTrendOutDTO> trend30Days,
        List<StatisticsBreakdownOutDTO> weeklyExpenseCategories,
        List<StatisticsBreakdownOutDTO> weeklyIncomeCategories,
        List<StatisticsBreakdownOutDTO> monthlyExpenseCategories,
        List<StatisticsBreakdownOutDTO> monthlyIncomeCategories,
        List<StatisticsBreakdownOutDTO> annualExpenseCategories,
        List<StatisticsBreakdownOutDTO> annualIncomeCategories,
        List<StatisticsBreakdownOutDTO> expenseCategories,
        List<StatisticsBreakdownOutDTO> incomeCategories,
        List<StatisticsBreakdownOutDTO> accounts,
        List<StatisticsTrendOutDTO> annual
) {
}
