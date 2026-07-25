package com.lhj.jizhang.user.dto;
import java.math.BigDecimal;
public record DuplicateFindingOutDTO(Long id, Long transactionId, Long candidateTransactionId,
                                     BigDecimal similarity, String reason, String status) { }
