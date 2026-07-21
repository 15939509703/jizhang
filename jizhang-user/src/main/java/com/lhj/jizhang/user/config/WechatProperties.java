package com.lhj.jizhang.user.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.wechat")
public record WechatProperties(
        @NotBlank String appId,
        @NotBlank String appSecret,
        @NotBlank String code2SessionUrl
) {
}
