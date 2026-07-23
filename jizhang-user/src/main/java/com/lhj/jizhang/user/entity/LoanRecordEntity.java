package com.lhj.jizhang.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("fin_loan_record")
public class LoanRecordEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String loanNo;
    private Long bookId;
    private Long createdUserId;
    private String loanType;
    private String counterpartyName;
    private BigDecimal totalAmount;
    private BigDecimal repaidAmount;
    private LocalDate dueDate;
    private String status;
    private Long relatedTransactionId;
    private String note;
    private Integer version;
    private String creator;
    private LocalDateTime createdTime;
    private String modifier;
    private LocalDateTime modifiedTime;
}
