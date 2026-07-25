package com.lhj.jizhang.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("fin_savings_goal")
public class SavingsGoalEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String goalNo;
    private Long bookId;
    private String name;
    private String description;
    private BigDecimal targetAmount;
    private BigDecimal initialAmount;
    private Long targetAccountId;
    private LocalDate startDate;
    private LocalDate targetDate;
    private String status;
    private Integer version;
    private Integer deletedFlag;
    private String creator;
    private LocalDateTime createdTime;
    private String modifier;
    private LocalDateTime modifiedTime;
}
