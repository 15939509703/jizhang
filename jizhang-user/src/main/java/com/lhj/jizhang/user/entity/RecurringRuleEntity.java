package com.lhj.jizhang.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("fin_recurring_rule")
public class RecurringRuleEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String ruleNo;
    private Long bookId;
    private Long createdUserId;
    private String transactionType;
    private Long categoryId;
    private Long accountId;
    private Long targetAccountId;
    private BigDecimal amount;
    private String title;
    private String note;
    private String recurrenceType;
    private Integer executionDay;
    private Integer monthEndFlag;
    private LocalTime executionTime;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate nextExecutionDate;
    private LocalDate lastExecutionDate;
    private String executionMode;
    private String status;
    private Integer version;
    private Integer deletedFlag;
    private String creator;
    private LocalDateTime createdTime;
    private String modifier;
    private LocalDateTime modifiedTime;
}
