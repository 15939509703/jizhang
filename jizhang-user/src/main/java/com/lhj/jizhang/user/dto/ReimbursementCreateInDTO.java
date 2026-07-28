package com.lhj.jizhang.user.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReimbursementCreateInDTO(
        Long expenseTransactionId,
        @Size(max = 128) String reimburserName,
        LocalDate submittedDate,
        LocalDate expectedDate,
        @Size(max = 1000) String note,
        Long bookId,
        @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal expectedAmount,
        @Pattern(regexp = "ADVANCE|INCOME") String reimbursementType
) {
    public ReimbursementCreateInDTO(Long expenseTransactionId, String reimburserName,
                                    LocalDate submittedDate, LocalDate expectedDate, String note,
                                    Long bookId, BigDecimal expectedAmount) {
        this(expenseTransactionId, reimburserName, submittedDate, expectedDate, note,
                bookId, expectedAmount, null);
    }

    public ReimbursementCreateInDTO(Long expenseTransactionId, String reimburserName,
                                    LocalDate submittedDate, LocalDate expectedDate, String note) {
        this(expenseTransactionId, reimburserName, submittedDate, expectedDate, note,
                null, null, null);
    }
}
