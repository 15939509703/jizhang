package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Schema(name = "LoanRecord", description = "借还记录")
public record LoanOutDTO(
        @Schema(description = "借还记录ID", example = "1")
        Long id,
        @Schema(description = "借还编号", example = "LOAN202607230001")
        String loanNo,
        @Schema(description = "账本ID", example = "1")
        Long bookId,
        @Schema(description = "创建用户ID", example = "1")
        Long createdUserId,
        @Schema(description = "借还类型", example = "LEND", allowableValues = {"BORROW", "LEND"})
        String loanType,
        @Schema(description = "往来人名称", example = "张三")
        String counterpartyName,
        @Schema(description = "总金额", example = "500.00")
        BigDecimal totalAmount,
        @Schema(description = "已还金额", example = "100.00")
        BigDecimal repaidAmount,
        @Schema(description = "剩余金额", example = "400.00")
        BigDecimal remainingAmount,
        @Schema(description = "到期日", example = "2026-08-01")
        LocalDate dueDate,
        @Schema(description = "状态", example = "OPEN", allowableValues = {"OPEN", "PARTIAL", "CLOSED", "OVERDUE"})
        String status,
        @Schema(description = "是否逾期", example = "false")
        boolean overdue,
        @Schema(description = "关联账单ID", example = "10")
        Long relatedTransactionId,
        @Schema(description = "备注和操作时间线")
        String note,
        @Schema(description = "乐观锁版本号", example = "1")
        Integer version,
        @Schema(description = "最后修改时间", example = "2026-07-23T10:00:00Z")
        Instant modifiedAt
) {
}
