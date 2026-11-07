package com.lhj.jizhang.user.service;

import com.lhj.jizhang.user.entity.RecurringRuleEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RecurringDateCalculatorTest {
    /** 每日规则从开始日期执行到结束日期。 */
    @Test
    void shouldScheduleDailyFromStartThroughEndDate() {
        RecurringRuleEntity rule = rule("DAILY", null, null, LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 11, 12));
        assertEquals(LocalDate.of(2026, 11, 10), RecurringDateCalculator.nextDate(LocalDate.of(2026, 11, 8), rule));
        assertEquals(LocalDate.of(2026, 11, 11), RecurringDateCalculator.nextDate(LocalDate.of(2026, 11, 11), rule));
        assertNull(RecurringDateCalculator.nextDate(LocalDate.of(2026, 11, 13), rule));
    }

    /** 每年二月二十九日在非闰年落到二月二十八日。 */
    @Test
    void shouldScheduleAnnualLeapDayOnFebruary28InNonLeapYears() {
        RecurringRuleEntity rule = rule("YEARLY", 2, 29, LocalDate.of(2023, 1, 1), null);
        assertEquals(LocalDate.of(2023, 2, 28), RecurringDateCalculator.nextDate(LocalDate.of(2023, 2, 1), rule));
        assertEquals(LocalDate.of(2024, 2, 29), RecurringDateCalculator.nextDate(LocalDate.of(2023, 3, 1), rule));
        assertEquals(LocalDate.of(2025, 2, 28), RecurringDateCalculator.nextDate(LocalDate.of(2024, 3, 1), rule));
    }

    /** 年度日期必须落在规则的开始和结束范围内。 */
    @Test
    void shouldRespectAnnualStartAndEndDates() {
        RecurringRuleEntity rule = rule("YEARLY", 11, 8, LocalDate.of(2026, 11, 9),
                LocalDate.of(2027, 11, 7));
        assertNull(RecurringDateCalculator.nextDate(LocalDate.of(2026, 1, 1), rule));
        rule.setEndDate(LocalDate.of(2027, 11, 8));
        assertEquals(LocalDate.of(2027, 11, 8), RecurringDateCalculator.nextDate(LocalDate.of(2026, 1, 1), rule));
    }

    /** 原有月度短月逻辑保持不变。 */
    @Test
    void shouldPreserveMonthlyShortMonthBehavior() {
        RecurringRuleEntity rule = rule("MONTHLY", null, 31, LocalDate.of(2026, 1, 1), null);
        assertEquals(LocalDate.of(2026, 2, 28), RecurringDateCalculator.nextDate(LocalDate.of(2026, 2, 1), rule));
    }

    /** 构造仅包含日期计算所需字段的周期规则。 */
    private RecurringRuleEntity rule(String type, Integer month, Integer day, LocalDate start, LocalDate end) {
        RecurringRuleEntity rule = new RecurringRuleEntity();
        rule.setRecurrenceType(type);
        rule.setExecutionMonth(month);
        rule.setExecutionDay(day);
        rule.setMonthEndFlag(0);
        rule.setStartDate(start);
        rule.setEndDate(end);
        return rule;
    }
}
