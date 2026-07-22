package com.lhj.jizhang.user.dto;

import java.math.BigDecimal;

public record StatisticsBreakdownOutDTO(
        Long id,
        String name,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal amount,
        Integer percent
) {
}
