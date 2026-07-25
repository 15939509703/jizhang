package com.lhj.jizhang.user.dto;

import java.time.Instant;
import java.time.LocalDate;

public record RecurringExecutionOutDTO(
        Long id,
        Long ruleId,
        String ruleTitle,
        String transactionType,
        java.math.BigDecimal amount,
        LocalDate scheduledDate,
        Long transactionId,
        String executionStatus,
        String failureReason,
        Instant executedAt,
        Long handledUserId
) {
}
