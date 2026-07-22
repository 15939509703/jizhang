package com.lhj.jizhang.user.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountEntryOutDTO(
        Long id,
        Long transactionId,
        String transactionTitle,
        String transactionType,
        BigDecimal signedAmount,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        Instant happenedAt
) {
}
