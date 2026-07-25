package com.lhj.jizhang.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("fin_account")
public class AccountEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String accountNo;
    private Long bookId;
    private String name;
    private String accountType;
    private String accountNature;
    private BigDecimal initialBalance;
    private BigDecimal currentBalance;
    private Integer includedInAssets;
    private Integer sortNo;
    private Integer status;
    private Integer version;
    private Integer deletedFlag;
    private LocalDateTime archivedTime;
    private String creator;
    private LocalDateTime createdTime;
    private String modifier;
    private LocalDateTime modifiedTime;
}
