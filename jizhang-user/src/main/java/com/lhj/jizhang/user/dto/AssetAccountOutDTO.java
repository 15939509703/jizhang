package com.lhj.jizhang.user.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AssetAccountOutDTO(
        Long id,
        String name,
        String accountType,
        String accountNature,
        BigDecimal currentBalance,
        Instant lastChangedAt
) {
}
