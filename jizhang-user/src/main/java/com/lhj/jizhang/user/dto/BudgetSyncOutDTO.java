package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "BudgetSyncItem", description = "预算同步数据")
public record BudgetSyncOutDTO(
        @Schema(description = "预算ID", example = "1")
        Long id,
        @Schema(description = "预算编号", example = "BUD202607220001")
        String budgetNo,
        @Schema(description = "账本ID", example = "1")
        Long bookId,
        @Schema(description = "预算月份", example = "2026-07")
        String month,
        @Schema(description = "总预算", example = "5000.00")
        BigDecimal totalLimit,
        @Schema(description = "预警比例", example = "0.8000")
        BigDecimal warningRate,
        @Schema(description = "状态", example = "1")
        Integer status,
        @Schema(description = "版本号", example = "1")
        Integer version,
        @Schema(description = "最后修改时间")
        Instant modifiedAt
) {
}
