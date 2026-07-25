package com.lhj.jizhang.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("fin_reimbursement")
public class ReimbursementEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String reimbursementNo;
    private Long bookId;
    private Long expenseTransactionId;
    private Long reimbursementTransactionId;
    private BigDecimal expectedAmount;
    private String reimburserName;
    private LocalDate submittedDate;
    private LocalDate expectedDate;
    private LocalDateTime reimbursedTime;
    private String status;
    private String note;
    private Integer version;
    private String creator;
    private LocalDateTime createdTime;
    private String modifier;
    private LocalDateTime modifiedTime;
}
