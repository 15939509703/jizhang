package com.lhj.jizhang.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jizhang.features")
public record FeatureProperties(
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
