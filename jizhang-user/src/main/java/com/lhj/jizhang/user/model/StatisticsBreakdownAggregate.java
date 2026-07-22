package com.lhj.jizhang.user.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class StatisticsBreakdownAggregate {
    private Long itemId;
    private String itemName;
    private BigDecimal incomeAmount;
    private BigDecimal expenseAmount;
}
