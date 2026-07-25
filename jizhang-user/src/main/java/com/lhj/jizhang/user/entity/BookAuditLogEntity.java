package com.lhj.jizhang.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("fin_book_audit_log")
public class BookAuditLogEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long bookId;
    private Long operatorUserId;
    private String action;
    private String objectType;
    private Long objectId;
    private String summary;
    private LocalDateTime createdTime;
}
