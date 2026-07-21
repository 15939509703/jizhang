package com.lhj.jizhang.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("fin_transaction")
public class TransactionEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String transactionNo;
    private String requestId;
    private Long bookId;
    private Long createdUserId;
    private String transactionType;
    private Long categoryId;
    private Long originalTransactionId;
    private BigDecimal amount;
    private String currencyCode;
    private LocalDateTime happenedAt;
    private String title;
    private String note;
    private String status;
    private Integer version;
    private String creator;
    private LocalDateTime createdTime;
    private String modifier;
    private LocalDateTime modifiedTime;
}
