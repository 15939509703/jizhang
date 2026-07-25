package com.lhj.jizhang.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.wechat.notification")
public record WechatNotificationProperties(
        boolean enabled,
        String recurringTemplateId,
        String budgetTemplateId,
        String tokenUrl,
        String sendUrl
) { }
