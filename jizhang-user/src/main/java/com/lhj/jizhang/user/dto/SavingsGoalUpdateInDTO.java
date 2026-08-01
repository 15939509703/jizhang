package com.lhj.jizhang.user.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SavingsGoalUpdateInDTO(
        @NotBlank @Size(max = 128) String name,
        @Size(max = 1000) String description,
        @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal targetAmount,
        Long targetAccountId,
        @NotNull LocalDate startDate,
        LocalDate targetDate,
        @NotNull Integer version
) { }
