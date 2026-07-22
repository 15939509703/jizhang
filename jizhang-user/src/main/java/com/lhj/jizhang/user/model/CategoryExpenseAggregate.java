package com.lhj.jizhang.user.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CategoryExpenseAggregate {
    private Long categoryId;
    private BigDecimal expenseAmount;
}
