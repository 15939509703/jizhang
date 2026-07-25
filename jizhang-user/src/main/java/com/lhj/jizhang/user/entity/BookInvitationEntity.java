package com.lhj.jizhang.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("fin_book_invitation")
public class BookInvitationEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String invitationNo;
    private Long bookId;
    private Long inviterUserId;
    private String defaultRole;
    private String tokenHash;
    private LocalDateTime expiredTime;
    private String status;
    private Long acceptedUserId;
    private LocalDateTime acceptedTime;
    private String creator;
    private LocalDateTime createdTime;
    private String modifier;
    private LocalDateTime modifiedTime;
}
