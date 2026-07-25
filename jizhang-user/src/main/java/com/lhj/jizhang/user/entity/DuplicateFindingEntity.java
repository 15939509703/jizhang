package com.lhj.jizhang.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("fin_duplicate_finding")
public class DuplicateFindingEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long bookId;
    private Long transactionId;
    private Long candidateTransactionId;
    private BigDecimal similarity;
    private String reason;
    private String status;
    private Long handledUserId;
    private String creator;
    private LocalDateTime createdTime;
    private String modifier;
    private LocalDateTime modifiedTime;
}
