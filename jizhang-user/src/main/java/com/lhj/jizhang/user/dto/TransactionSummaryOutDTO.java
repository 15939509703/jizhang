package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "TransactionSummary", description = "账本月度收支汇总")
public record TransactionSummaryOutDTO(
        @Schema(description = "统计月份", example = "2026-07")
        String month,
        @Schema(description = "收入合计", example = "12000.00")
        BigDecimal income,
        @Schema(description = "支出合计", example = "3456.78")
        BigDecimal expense,
        @Schema(description = "结余，等于收入减支出", example = "8543.22")
        BigDecimal balance,
        @Schema(description = "账本币种", example = "CNY")
        String currencyCode
) {
}
