package com.lhj.jizhang.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class UserEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String userNo;
    private String nickName;
    private String avatarUrl;
    private String timezone;
    private Integer status;
    private LocalDateTime lastLoginTime;
    private String creator;
    private String modifier;
}
