package com.lhj.jizhang.user.service;

import com.lhj.jizhang.user.entity.RecurringRuleEntity;

import java.time.LocalDate;
import java.time.YearMonth;

public final class RecurringDateCalculator {
    private RecurringDateCalculator() {
    }

    /** 计算不早于指定日期且不超过规则结束日期的下一次执行日。 */
    public static LocalDate nextDate(LocalDate fromDate, RecurringRuleEntity rule) {
        LocalDate anchor = fromDate.isAfter(rule.getStartDate()) ? fromDate : rule.getStartDate();
        LocalDate candidate = switch (rule.getRecurrenceType()) {
            case "DAILY" -> anchor;
            case "MONTHLY" -> MonthlyRecurrenceCalculator.nextDate(anchor, rule.getStartDate(), null,
                    rule.getExecutionDay(), rule.getMonthEndFlag() == 1);
            case "YEARLY" -> nextAnnualDate(anchor, rule.getExecutionMonth(), rule.getExecutionDay());
            default -> throw new IllegalArgumentException("不支持的周期类型: " + rule.getRecurrenceType());
        };
        return rule.getEndDate() != null && candidate.isAfter(rule.getEndDate()) ? null : candidate;
    }

    /** 每年 2 月 29 日在非闰年落到该月最后一天。 */
    private static LocalDate nextAnnualDate(LocalDate anchor, int month, int day) {
        LocalDate candidate = annualDate(anchor.getYear(), month, day);
        return candidate.isBefore(anchor) ? annualDate(anchor.getYear() + 1, month, day) : candidate;
    }

    /** 将年度执行日限制在当年所选月份的实际天数内。 */
    private static LocalDate annualDate(int year, int month, int day) {
        YearMonth target = YearMonth.of(year, month);
        return target.atDay(Math.min(day, target.lengthOfMonth()));
    }
}
