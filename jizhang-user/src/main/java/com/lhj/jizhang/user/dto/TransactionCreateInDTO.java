package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "TransactionCreateRequest", description = "新增账单请求")
public record TransactionCreateInDTO(
        @Schema(description = "客户端生成的幂等请求ID", example = "01J2ABCDEF1234567890")
        @NotBlank(message = "请求ID不能为空")
        @Size(max = 64, message = "请求ID长度不能超过64个字符")
        String requestId,
        @Schema(description = "账本ID", example = "1")
        @NotNull(message = "账本ID不能为空") Long bookId,
        @Schema(description = "账单类型", example = "EXPENSE",
                allowableValues = {"EXPENSE", "INCOME", "TRANSFER"})
        @NotBlank(message = "账单类型不能为空")
        @Pattern(regexp = "EXPENSE|INCOME|TRANSFER", message = "账单类型不正确")
        String transactionType,
        @Schema(description = "分类ID，转账时不传", example = "1")
        Long categoryId,
        @Schema(description = "复制来源账单ID", example = "10")
        Long originalTransactionId,
        @Schema(description = "主账户ID", example = "1")
        @NotNull(message = "账户ID不能为空") Long accountId,
        @Schema(description = "转入账户ID，仅转账时必填", example = "2")
        Long targetAccountId,
        @Schema(description = "账单金额，必须大于0", example = "28.50")
        @NotNull(message = "金额不能为空")
        @DecimalMin(value = "0.01", message = "金额必须大于0")
        @Digits(integer = 17, fraction = 2, message = "金额最多保留2位小数")
        BigDecimal amount,
        @Schema(description = "账单发生时间（ISO-8601）", example = "2026-07-21T10:00:00Z")
        @NotNull(message = "发生时间不能为空") Instant happenedAt,
        @Schema(description = "账单标题", example = "午餐")
        @NotBlank(message = "账单标题不能为空")
        @Size(max = 128, message = "账单标题长度不能超过128个字符")
        String title,
        @Schema(description = "账单备注", example = "工作餐")
        @Size(max = 1000, message = "备注长度不能超过1000个字符")
        String note
) {
}
