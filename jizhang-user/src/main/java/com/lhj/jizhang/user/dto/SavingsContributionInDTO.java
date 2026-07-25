package com.lhj.jizhang.user.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record SavingsContributionInDTO(
        @NotBlank @Size(max = 64) String requestId,
        @NotBlank @Pattern(regexp = "MANUAL|TRANSFER") String contributionType,
        @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotNull LocalDate contributionDate,
        Long sourceAccountId,
        Instant happenedAt,
        @Size(max = 1000) String note
) { }
