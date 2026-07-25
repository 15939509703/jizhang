package com.lhj.jizhang.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_wechat_subscription")
public class WechatSubscriptionEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String scene;
    private Integer enabledFlag;
    private Integer authorizedCount;
    private LocalDateTime lastAuthorizedTime;
    private String creator;
    private LocalDateTime createdTime;
    private String modifier;
    private LocalDateTime modifiedTime;
}
