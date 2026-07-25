package com.lhj.jizhang.user.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ReimbursementOutDTO(
        Long id, String reimbursementNo, Long bookId, Long expenseTransactionId,
        Long reimbursementTransactionId, BigDecimal expectedAmount, String reimburserName,
        LocalDate submittedDate, LocalDate expectedDate, Instant reimbursedTime,
        String status, String note, Integer version
) { }
