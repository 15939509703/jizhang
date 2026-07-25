package com.lhj.jizhang.user.dto;

import java.math.BigDecimal;

public record ConsumptionComparisonOutDTO(BigDecimal baseAmount, BigDecimal changeAmount, BigDecimal changeRate) { }
