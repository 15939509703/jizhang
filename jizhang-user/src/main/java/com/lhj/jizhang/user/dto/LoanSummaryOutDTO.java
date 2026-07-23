package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "LoanSummary", description = "借还汇总")
public record LoanSummaryOutDTO(
        @Schema(description = "借入待还金额", example = "1200.00")
        BigDecimal payableAmount,
        @Schema(description = "借出待收金额", example = "800.00")
        BigDecimal receivableAmount,
        @Schema(description = "逾期金额", example = "300.00")
        BigDecimal overdueAmount,
        @Schema(description = "未结清记录数", example = "3")
        long openCount
) {
}
