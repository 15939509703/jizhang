package com.lhj.jizhang.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_export_task")
public class ExportTaskEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskNo;
    private Long userId;
    private Long bookId;
    private String exportType;
    private String queryJson;
    private String taskStatus;
    private String fileObjectKey;
    private String failureReason;
    private LocalDateTime startedTime;
    private LocalDateTime finishedTime;
    private LocalDateTime expiredTime;
    private String creator;
    private LocalDateTime createdTime;
    private String modifier;
    private LocalDateTime modifiedTime;
}
