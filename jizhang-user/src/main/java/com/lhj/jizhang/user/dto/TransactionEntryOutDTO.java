package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "TransactionEntry", description = "账单账户余额分录")
public record TransactionEntryOutDTO(
        @Schema(description = "分录ID", example = "1")
        Long id,
        @Schema(description = "账户ID", example = "1")
        Long accountId,
        @Schema(description = "余额分录类型", example = "DECREASE",
                allowableValues = {"INCREASE", "DECREASE", "REVERSAL"})
        String entryType,
        @Schema(description = "带符号变动金额", example = "-28.50")
        BigDecimal signedAmount,
        @Schema(description = "变动前余额", example = "1000.00")
        BigDecimal balanceBefore,
        @Schema(description = "变动后余额", example = "971.50")
        BigDecimal balanceAfter
) {
}
