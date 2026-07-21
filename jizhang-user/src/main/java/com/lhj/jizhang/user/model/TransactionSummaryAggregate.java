package com.lhj.jizhang.user.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransactionSummaryAggregate {
    private BigDecimal incomeAmount;
    private BigDecimal expenseAmount;
}
