package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "WechatLoginResponse", description = "微信登录结果")
public record WechatLoginOutDTO(
        @Schema(description = "JWT访问令牌", example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,
        @Schema(description = "刷新令牌", example = "6e0f5c9c...")
        String refreshToken,
        @Schema(description = "访问令牌有效期，单位秒", example = "1800")
        long expiresIn,
        @Schema(description = "当前用户")
        LoginUserOutDTO user,
        @Schema(description = "默认账本")
        LoginBookOutDTO defaultBook
) {
}
