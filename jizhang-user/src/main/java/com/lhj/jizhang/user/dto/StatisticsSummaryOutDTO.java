package com.lhj.jizhang.user.dto;

import java.math.BigDecimal;

public record StatisticsSummaryOutDTO(
        BigDecimal income,
        BigDecimal expense,
        BigDecimal balance
) {
}
