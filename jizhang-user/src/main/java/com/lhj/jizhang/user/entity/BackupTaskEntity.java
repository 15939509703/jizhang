package com.lhj.jizhang.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("fin_backup_task")
public class BackupTaskEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskNo;
    private Long userId;
    private Long bookId;
    private String filePath;
    private String checksum;
    private Integer formatVersion;
    private String taskStatus;
    private String failureCode;
    private LocalDateTime expiredTime;
    private String creator;
    private LocalDateTime createdTime;
    private String modifier;
    private LocalDateTime modifiedTime;
}
