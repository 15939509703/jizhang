package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "ExportTask", description = "数据导出任务")
public record ExportTaskOutDTO(
        @Schema(description = "任务ID", example = "1")
        Long id,
        @Schema(description = "任务编号", example = "EXP202607230001")
        String taskNo,
        @Schema(description = "账本ID", example = "1")
        Long bookId,
        @Schema(description = "导出类型", example = "CSV", allowableValues = {"CSV", "EXCEL"})
        String exportType,
        @Schema(description = "任务状态", example = "SUCCESS", allowableValues = {"PENDING", "PROCESSING", "SUCCESS", "FAILED"})
        String taskStatus,
        @Schema(description = "下载路径")
        String downloadUrl,
        @Schema(description = "失败原因")
        String failureReason,
        @Schema(description = "完成时间")
        Instant finishedAt,
        @Schema(description = "过期时间")
        Instant expiredAt
) {
}
