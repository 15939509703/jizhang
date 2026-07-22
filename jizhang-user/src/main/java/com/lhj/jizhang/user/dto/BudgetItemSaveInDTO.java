package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(name = "BudgetItemSaveRequest", description = "分类预算保存请求")
public record BudgetItemSaveInDTO(
        @Schema(description = "支出分类ID", example = "1")
        @NotNull(message = "分类ID不能为空") Long categoryId,
        @Schema(description = "分类预算额度", example = "1800.00")
        @NotNull(message = "分类预算额度不能为空")
        @DecimalMin(value = "0.00", message = "分类预算额度不能小于0")
        @Digits(integer = 17, fraction = 2, message = "分类预算额度最多保留2位小数")
        BigDecimal limitAmount,
        @Schema(description = "预警比例，0到1之间", example = "0.8000")
        @NotNull(message = "分类预算预警比例不能为空")
        @DecimalMin(value = "0.0001", message = "分类预算预警比例必须大于0")
        @DecimalMax(value = "1.0000", message = "分类预算预警比例不能大于1")
        @Digits(integer = 1, fraction = 4, message = "分类预算预警比例最多保留4位小数")
        BigDecimal warningRate
) {
}
