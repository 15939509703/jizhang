package com.lhj.jizhang.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ReimbursementCreateInDTO(
        @NotNull Long expenseTransactionId,
        @Size(max = 128) String reimburserName,
        LocalDate submittedDate,
        LocalDate expectedDate,
        @Size(max = 1000) String note
) { }
