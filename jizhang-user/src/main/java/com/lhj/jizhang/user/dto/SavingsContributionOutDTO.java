package com.lhj.jizhang.user.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SavingsContributionOutDTO(
        Long id, String contributionNo, Long transactionId, String contributionType,
        BigDecimal amount, LocalDate contributionDate, String status, String note
) { }
