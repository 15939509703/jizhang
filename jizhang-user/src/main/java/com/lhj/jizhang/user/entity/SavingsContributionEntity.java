package com.lhj.jizhang.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("fin_savings_contribution")
public class SavingsContributionEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String contributionNo;
    private Long goalId;
    private Long transactionId;
    private String contributionType;
    private BigDecimal amount;
    private LocalDate contributionDate;
    private String status;
    private String note;
    private String creator;
    private LocalDateTime createdTime;
    private String modifier;
    private LocalDateTime modifiedTime;
}
