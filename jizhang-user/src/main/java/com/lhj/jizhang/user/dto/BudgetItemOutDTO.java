package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "BudgetItem", description = "分类预算信息")
public record BudgetItemOutDTO(
        @Schema(description = "分类预算ID", example = "1")
        Long id,
        @Schema(description = "支出分类ID", example = "1")
        Long categoryId,
        @Schema(description = "支出分类名称", example = "餐饮")
        String categoryName,
        @Schema(description = "分类预算额度", example = "1800.00")
        BigDecimal limitAmount,
        @Schema(description = "分类预算预警比例", example = "0.8000")
        BigDecimal warningRate,
        @Schema(description = "分类已使用额度", example = "1296.00")
        BigDecimal usedAmount,
        @Schema(description = "分类剩余额度", example = "504.00")
        BigDecimal remainingAmount,
        @Schema(description = "使用比例，0到1之间", example = "0.7200")
        BigDecimal usageRate,
        @Schema(description = "使用百分比", example = "72")
        Integer usagePercent,
        @Schema(description = "使用状态：UNSET、OK、WARN、OVER", example = "OK")
        String usageStatus
) {
}
