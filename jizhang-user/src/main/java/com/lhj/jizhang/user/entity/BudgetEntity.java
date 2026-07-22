package com.lhj.jizhang.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("fin_budget")
public class BudgetEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String budgetNo;
    private Long bookId;
    private String budgetMonth;
    private BigDecimal totalLimit;
    private BigDecimal warningRate;
    private Integer status;
    private Integer version;
    private String creator;
    private LocalDateTime createdTime;
    private String modifier;
    private LocalDateTime modifiedTime;
}
