package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "AssetAccount", description = "资产总览账户信息")
public record AssetAccountOutDTO(
        @Schema(description = "账户ID", example = "1")
        Long id,
        @Schema(description = "账户名称", example = "招商银行信用卡")
        String name,
        @Schema(description = "账户类型", example = "CREDIT")
        String accountType,
        @Schema(description = "账户性质", example = "LIABILITY", allowableValues = {"ASSET", "LIABILITY"})
        String accountNature,
        @Schema(description = "初始余额；负债账户表示信用额度", example = "10000.00")
        BigDecimal initialBalance,
        @Schema(description = "当前余额；负债账户表示当前可用额度", example = "8000.00")
        BigDecimal currentBalance,
        @Schema(description = "待还金额；资产账户固定为0", example = "2000.00")
        BigDecimal outstandingBalance,
        @Schema(description = "最近变动时间")
        Instant lastChangedAt
) {
}
