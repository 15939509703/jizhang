package com.lhj.jizhang.user.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ConsumptionInsightAggregate {
    private BigDecimal maxExpense;
    private Integer expenseDays;
    private Long topCategoryId;
    private String topCategoryName;
    private Integer topCategoryCount;
}
