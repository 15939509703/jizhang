package com.lhj.jizhang.user.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SavingsGoalOutDTO(
        Long id, String goalNo, Long bookId, String name, String description,
        BigDecimal targetAmount, BigDecimal completedAmount, BigDecimal remainingAmount,
        BigDecimal progressRate, BigDecimal monthlySuggestion, Long targetAccountId,
        LocalDate startDate, LocalDate targetDate, boolean overdue, String status, Integer version,
        List<SavingsContributionOutDTO> contributions
) { }
