package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(name = "Budget", description = "月度预算信息")
public record BudgetOutDTO(
        @Schema(description = "预算ID", example = "1")
        Long id,
        @Schema(description = "预算编号", example = "BUD202607220001")
        String budgetNo,
        @Schema(description = "账本ID", example = "1")
        Long bookId,
        @Schema(description = "预算月份，格式yyyy-MM", example = "2026-07")
        String month,
        @Schema(description = "账本月总预算额度", example = "5000.00")
        BigDecimal totalLimit,
        @Schema(description = "账本预算预警比例", example = "0.8000")
        BigDecimal warningRate,
        @Schema(description = "账本已使用额度", example = "3456.78")
        BigDecimal usedAmount,
        @Schema(description = "账本剩余额度", example = "1543.22")
        BigDecimal remainingAmount,
        @Schema(description = "使用比例，0到1之间", example = "0.6914")
        BigDecimal usageRate,
        @Schema(description = "使用百分比", example = "69")
        Integer usagePercent,
        @Schema(description = "预算状态：1有效，0停用", example = "1")
        Integer status,
        @Schema(description = "使用状态：UNSET、OK、WARN、OVER", example = "OK")
        String usageStatus,
        @Schema(description = "账本币种", example = "CNY")
        String currencyCode,
        @Schema(description = "分类预算列表")
        List<BudgetItemOutDTO> items
) {
}
