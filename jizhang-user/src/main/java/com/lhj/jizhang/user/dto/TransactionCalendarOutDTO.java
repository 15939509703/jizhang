package com.lhj.jizhang.user.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TransactionCalendarOutDTO(
        Long bookId,
        String month,
        LocalDate today,
        String currencyCode,
        BigDecimal incomeAmount,
        BigDecimal expenseAmount,
        Integer transactionCount,
        List<TransactionCalendarDayOutDTO> days
) {
}
