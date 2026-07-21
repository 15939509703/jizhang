package com.lhj.jizhang.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("fin_account_entry")
public class AccountEntryEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long transactionId;
    private Long accountId;
    private String entryType;
    private BigDecimal signedAmount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private LocalDateTime happenedAt;
    private String creator;
    private LocalDateTime createdTime;
    private String modifier;
    private LocalDateTime modifiedTime;
}
