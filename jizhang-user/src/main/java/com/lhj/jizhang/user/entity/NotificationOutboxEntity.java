package com.lhj.jizhang.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_notification_outbox")
public class NotificationOutboxEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventKey;
    private Long userId;
    private String scene;
    private String templateId;
    private String pagePath;
    private String parametersJson;
    private String status;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private LocalDateTime expiredTime;
    private String failureCode;
    private LocalDateTime createdTime;
    private LocalDateTime modifiedTime;
}
