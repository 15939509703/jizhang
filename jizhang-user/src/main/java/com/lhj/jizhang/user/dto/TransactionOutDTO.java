package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(name = "Transaction", description = "账单详情")
public record TransactionOutDTO(
        @Schema(description = "账单ID", example = "1")
        Long id,
        @Schema(description = "账单编号", example = "TXN202607210001")
        String transactionNo,
        @Schema(description = "客户端幂等请求ID", example = "01J2ABCDEF1234567890")
        String requestId,
        @Schema(description = "账本ID", example = "1")
        Long bookId,
        @Schema(description = "创建用户ID", example = "1")
        Long createdUserId,
        @Schema(description = "账单类型", example = "EXPENSE",
                allowableValues = {"EXPENSE", "INCOME", "TRANSFER"})
        String transactionType,
        @Schema(description = "分类ID", example = "1")
        Long categoryId,
        @Schema(description = "原账单ID，复制账单时记录来源", example = "10")
        Long originalTransactionId,
        @Schema(description = "账单金额", example = "28.50")
        BigDecimal amount,
        @Schema(description = "币种代码", example = "CNY")
        String currencyCode,
        @Schema(description = "账单发生时间", example = "2026-07-21T10:00:00Z")
        Instant happenedAt,
        @Schema(description = "账单标题", example = "午餐")
        String title,
        @Schema(description = "账单备注", example = "工作餐")
        String note,
        @Schema(description = "账单状态", example = "EFFECTIVE",
                allowableValues = {"EFFECTIVE", "VOIDED", "REVERSED"})
        String status,
        @Schema(description = "乐观锁版本号", example = "1")
        Integer version,
        @Schema(description = "账户余额分录")
        List<TransactionEntryOutDTO> entries,
        @Schema(description = "账单图片附件")
        List<TransactionAttachmentOutDTO> attachments
) {
}
