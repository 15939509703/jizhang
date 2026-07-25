package com.lhj.jizhang.user.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TransactionCalendarDayOutDTO(
        LocalDate date,
        BigDecimal incomeAmount,
        BigDecimal expenseAmount,
        Integer transactionCount,
        Integer pendingRecurringCount,
        Instant startAt,
        Instant endAt
) {
}
