package com.lhj.jizhang.user.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record DuplicateFindingOutDTO(Long id, Long transactionId, Long candidateTransactionId,
                                     BigDecimal similarity, String reason, String status,
                                     /** 保留的原账单摘要。 */ TransactionSummary transaction,
                                     /** 确认重复时将作废的候选账单摘要。 */ TransactionSummary candidateTransaction) {
    /** 用于核对疑似重复账单的关键字段。 */
    public record TransactionSummary(
            /** 账单 ID。 */ Long id,
            /** 标题。 */ String title,
            /** 类型。 */ String transactionType,
            /** 金额。 */ BigDecimal amount,
            /** 币种。 */ String currencyCode,
            /** 发生时间。 */ Instant happenedAt,
            /** 分类名称。 */ String categoryName,
            /** 备注。 */ String note,
            /** 当前状态。 */ String status) { }
}
