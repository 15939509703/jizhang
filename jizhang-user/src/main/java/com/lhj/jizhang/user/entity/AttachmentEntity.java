package com.lhj.jizhang.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("fin_attachment")
public class AttachmentEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long transactionId;
    private String storageProvider;
    private String objectKey;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private String fileHash;
    private Integer status;
    private String creator;
    private LocalDateTime createdTime;
    private String modifier;
    private LocalDateTime modifiedTime;
}
