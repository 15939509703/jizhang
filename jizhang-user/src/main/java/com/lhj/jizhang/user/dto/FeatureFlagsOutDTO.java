package com.lhj.jizhang.user.dto;

public record FeatureFlagsOutDTO(
        boolean recurringTransactions,
        boolean transactionCalendar,
        boolean budgetForecast,
        boolean assetDashboard,
        boolean reimbursement,
        boolean savingsGoals,
        boolean consumptionReports,
        boolean dataSecurity,
        boolean familySharing,
        boolean wechatSubscriptions
) {
}
