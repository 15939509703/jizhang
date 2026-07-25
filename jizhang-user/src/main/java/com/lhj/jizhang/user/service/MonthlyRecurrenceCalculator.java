package com.lhj.jizhang.user.service;

import java.time.LocalDate;
import java.time.YearMonth;

public final class MonthlyRecurrenceCalculator {
    private MonthlyRecurrenceCalculator() {
    }

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

    private static LocalDate candidate(YearMonth month, int executionDay, boolean monthEnd) {
        int day = monthEnd ? month.lengthOfMonth() : Math.min(executionDay, month.lengthOfMonth());
        return month.atDay(day);
    }
}
