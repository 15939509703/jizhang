package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "Account", description = "账户信息")
public record AccountOutDTO(
        @Schema(description = "账户ID", example = "1")
        Long id,
        @Schema(description = "账户编号", example = "ACC202607210001")
        String accountNo,
        @Schema(description = "账本ID", example = "1")
        Long bookId,
        @Schema(description = "账户名称", example = "微信零钱")
        String name,
        @Schema(description = "账户类型", example = "WECHAT",
                allowableValues = {"CASH", "BANK", "WECHAT", "ALIPAY", "CREDIT"})
        String accountType,
        @Schema(description = "账户性质", example = "ASSET", allowableValues = {"ASSET", "LIABILITY"})
        String accountNature,
        @Schema(description = "初始余额", example = "1000.00")
        BigDecimal initialBalance,
        @Schema(description = "当前余额", example = "971.50")
        BigDecimal currentBalance,
        @Schema(description = "是否计入总资产", example = "true")
        boolean includedInAssets,
        @Schema(description = "排序号", example = "10")
        Integer sortNo,
        @Schema(description = "账户状态：1有效，0停用", example = "1")
        Integer status,
        @Schema(description = "乐观锁版本号", example = "2")
        Integer version,
        @Schema(description = "创建时间", example = "2026-07-23T10:00:00Z")
        Instant createdAt
) {
}
