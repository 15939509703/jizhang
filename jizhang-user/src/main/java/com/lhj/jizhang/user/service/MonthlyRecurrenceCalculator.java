package com.lhj.jizhang.user.service;

import java.time.LocalDate;
import java.time.YearMonth;

public final class MonthlyRecurrenceCalculator {
    /**
     * 初始化按月重复日期计算器。
     */
    private MonthlyRecurrenceCalculator() {
    }

    /**
     * 计算不早于指定日期的下次月度执行日。
     *
     * @param fromDate 日期
     * @param startDate 起始日期
     * @param endDate 结束日期
     * @param executionDay 每月执行日
     * @param monthEnd 是否启用
     * @return 计算后的日期
     */
    public static LocalDate nextDate(
            LocalDate fromDate,
            LocalDate startDate,
            LocalDate endDate,
            int executionDay,
            boolean monthEnd
    ) {
        LocalDate anchor = fromDate.isAfter(startDate) ? fromDate : startDate;
        YearMonth month = YearMonth.from(anchor);
        LocalDate candidate = candidate(month, executionDay, monthEnd);
        if (candidate.isBefore(anchor)) {
            candidate = candidate(month.plusMonths(1), executionDay, monthEnd);
        }
        return endDate != null && candidate.isAfter(endDate) ? null : candidate;
    }

    /**
     * 计算指定月份内有效的执行日期。
     *
     * @param month 目标月份
     * @param executionDay 每月执行日
     * @param monthEnd 是否启用
     * @return 计算后的日期
     */
    private static LocalDate candidate(YearMonth month, int executionDay, boolean monthEnd) {
        int day = monthEnd ? month.lengthOfMonth() : Math.min(executionDay, month.lengthOfMonth());
        return month.atDay(day);
    }
}
