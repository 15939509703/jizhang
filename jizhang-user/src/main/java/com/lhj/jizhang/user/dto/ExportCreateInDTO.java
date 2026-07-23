package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

@Schema(name = "ExportCreateRequest", description = "创建数据导出任务请求")
public record ExportCreateInDTO(
        @Schema(description = "账本ID", example = "1")
        @NotNull(message = "账本ID不能为空") Long bookId,
        @Schema(description = "导出类型", example = "CSV", allowableValues = {"CSV", "EXCEL"})
        @NotBlank(message = "导出类型不能为空")
        @Pattern(regexp = "CSV|EXCEL", message = "导出类型不正确")
        String exportType,
        @Schema(description = "账单类型", example = "EXPENSE", allowableValues = {"EXPENSE", "INCOME", "TRANSFER"})
        @Pattern(regexp = "EXPENSE|INCOME|TRANSFER", message = "账单类型不正确")
        String type,
        @Schema(description = "状态", example = "EFFECTIVE")
        String status,
        @Schema(description = "分类ID", example = "1")
        Long categoryId,
        @Schema(description = "账户ID", example = "1")
        Long accountId,
        @Schema(description = "关键词")
        String keyword,
        @Schema(description = "开始时间", example = "2026-07-01T00:00:00Z")
        Instant startAt,
        @Schema(description = "结束时间", example = "2026-08-01T00:00:00Z")
        Instant endAt
) {
}
