package com.lhj.jizhang.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("fin_restore_check")
public class RestoreCheckEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long backupId;
    private Long userId;
    private Integer addedCount;
    private Integer skippedCount;
    private Integer conflictCount;
    private String reportJson;
    private String status;
    private LocalDateTime expiredTime;
    private String creator;
    private LocalDateTime createdTime;
    private String modifier;
    private LocalDateTime modifiedTime;
}
