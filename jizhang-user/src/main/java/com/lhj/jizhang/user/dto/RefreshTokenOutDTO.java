package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "RefreshTokenResponse", description = "刷新后的令牌信息")
public record RefreshTokenOutDTO(
        @Schema(description = "新的访问令牌")
        String accessToken,
        @Schema(description = "轮换后的刷新令牌")
        String refreshToken,
        @Schema(description = "访问令牌有效秒数", example = "1800")
        long expiresIn
) {
}
