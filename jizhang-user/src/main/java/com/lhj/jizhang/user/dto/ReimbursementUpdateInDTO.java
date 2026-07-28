package com.lhj.jizhang.user.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReimbursementUpdateInDTO(
        @Size(max = 128) String reimburserName,
        LocalDate submittedDate,
        LocalDate expectedDate,
        @Size(max = 1000) String note,
        @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal expectedAmount,
        @NotNull Integer version,
        @Pattern(regexp = "ADVANCE|INCOME") String reimbursementType
) {
    public ReimbursementUpdateInDTO(String reimburserName, LocalDate submittedDate,
                                    LocalDate expectedDate, String note, BigDecimal expectedAmount,
                                    Integer version) {
        this(reimburserName, submittedDate, expectedDate, note, expectedAmount, version, null);
    }
}
