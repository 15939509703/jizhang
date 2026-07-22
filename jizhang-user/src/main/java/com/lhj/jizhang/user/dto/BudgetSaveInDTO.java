package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.List;

@Schema(name = "BudgetSaveRequest", description = "创建或修改月度预算请求")
public record BudgetSaveInDTO(
        @Schema(description = "账本ID", example = "1")
        @NotNull(message = "账本ID不能为空") Long bookId,
        @Schema(description = "预算月份，格式yyyy-MM", example = "2026-07")
        @NotBlank(message = "预算月份不能为空")
        @Pattern(regexp = "^[0-9]{4}-(0[1-9]|1[0-2])$", message = "预算月份格式不正确")
        String month,
        @Schema(description = "账本月总预算额度", example = "5000.00")
        @NotNull(message = "总预算额度不能为空")
        @DecimalMin(value = "0.00", message = "总预算额度不能小于0")
        @Digits(integer = 17, fraction = 2, message = "总预算额度最多保留2位小数")
        BigDecimal totalLimit,
        @Schema(description = "账本预算预警比例，0到1之间", example = "0.8000")
        @NotNull(message = "总预算预警比例不能为空")
        @DecimalMin(value = "0.0001", message = "总预算预警比例必须大于0")
        @DecimalMax(value = "1.0000", message = "总预算预警比例不能大于1")
        @Digits(integer = 1, fraction = 4, message = "总预算预警比例最多保留4位小数")
        BigDecimal warningRate,
        @Schema(description = "分类预算列表")
        List<@Valid BudgetItemSaveInDTO> items
) {
}
