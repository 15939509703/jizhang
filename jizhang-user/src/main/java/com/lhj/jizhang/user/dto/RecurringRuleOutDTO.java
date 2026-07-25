package com.lhj.jizhang.user.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record RecurringRuleOutDTO(
        Long id,
        String ruleNo,
        Long bookId,
        Long createdUserId,
        String transactionType,
        Long categoryId,
        Long accountId,
        Long targetAccountId,
        BigDecimal amount,
        String title,
        String note,
        String recurrenceType,
        Integer executionDay,
        boolean monthEnd,
        LocalTime executionTime,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate nextExecutionDate,
        LocalDate lastExecutionDate,
        String executionMode,
        String status,
        Integer version,
        Instant createdAt,
        List<RecurringExecutionOutDTO> recentExecutions
) {
}
