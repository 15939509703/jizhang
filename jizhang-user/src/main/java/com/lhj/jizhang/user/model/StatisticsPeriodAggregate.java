package com.lhj.jizhang.user.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class StatisticsPeriodAggregate {
    private String periodKey;
    private BigDecimal incomeAmount;
    private BigDecimal expenseAmount;
}
