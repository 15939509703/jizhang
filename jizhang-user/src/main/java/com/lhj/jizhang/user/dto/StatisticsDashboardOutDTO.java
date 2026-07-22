package com.lhj.jizhang.user.dto;

import java.util.List;

public record StatisticsDashboardOutDTO(
        String month,
        Integer year,
        String currencyCode,
        StatisticsSummaryOutDTO monthSummary,
        StatisticsSummaryOutDTO todaySummary,
        List<StatisticsTrendOutDTO> trend7Days,
        List<StatisticsTrendOutDTO> trend30Days,
        List<StatisticsBreakdownOutDTO> expenseCategories,
        List<StatisticsBreakdownOutDTO> incomeCategories,
        List<StatisticsBreakdownOutDTO> accounts,
        List<StatisticsTrendOutDTO> annual
) {
}
