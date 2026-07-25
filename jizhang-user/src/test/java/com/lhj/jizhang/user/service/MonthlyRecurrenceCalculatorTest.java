package com.lhj.jizhang.user.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MonthlyRecurrenceCalculatorTest {
    @Test
    void shouldClampDay31ForShortAndLeapYearMonths() {
        assertEquals(LocalDate.of(2025, 2, 28), MonthlyRecurrenceCalculator.nextDate(
                LocalDate.of(2025, 2, 1), LocalDate.of(2025, 1, 1), null, 31, false));
        assertEquals(LocalDate.of(2024, 2, 29), MonthlyRecurrenceCalculator.nextDate(
                LocalDate.of(2024, 2, 1), LocalDate.of(2024, 1, 1), null, 31, false));
    }

    @Test
    void shouldUseActualMonthEndAndRespectStartDate() {
        assertEquals(LocalDate.of(2026, 4, 30), MonthlyRecurrenceCalculator.nextDate(
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 1, 1), null, 1, true));
        assertEquals(LocalDate.of(2026, 8, 15), MonthlyRecurrenceCalculator.nextDate(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 10), null, 15, false));
    }

    @Test
    void shouldStopWhenNextOccurrenceExceedsEndDate() {
        assertNull(MonthlyRecurrenceCalculator.nextDate(
                LocalDate.of(2026, 7, 16), LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 7, 31), 15, false));
    }
}
