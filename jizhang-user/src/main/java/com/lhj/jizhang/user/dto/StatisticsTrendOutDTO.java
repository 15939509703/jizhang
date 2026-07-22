package com.lhj.jizhang.user.dto;

import java.math.BigDecimal;

public record StatisticsTrendOutDTO(
        String period,
        BigDecimal income,
        BigDecimal expense
) {
}
