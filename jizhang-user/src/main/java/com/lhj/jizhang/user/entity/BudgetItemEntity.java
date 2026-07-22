package com.lhj.jizhang.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("fin_budget_item")
public class BudgetItemEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long budgetId;
    private Long categoryId;
    private BigDecimal limitAmount;
    private BigDecimal warningRate;
    private String creator;
    private LocalDateTime createdTime;
    private String modifier;
    private LocalDateTime modifiedTime;
}
