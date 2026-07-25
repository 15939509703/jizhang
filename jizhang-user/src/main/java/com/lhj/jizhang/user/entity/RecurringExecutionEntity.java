package com.lhj.jizhang.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("fin_recurring_execution")
public class RecurringExecutionEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ruleId;
    private LocalDate scheduledDate;
    private Long transactionId;
    private String executionStatus;
    private String failureReason;
    private LocalDateTime executedTime;
    private Long handledUserId;
    private String creator;
    private LocalDateTime createdTime;
    private String modifier;
    private LocalDateTime modifiedTime;
}
