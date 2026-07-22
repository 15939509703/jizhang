package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "RefreshTokenRequest", description = "刷新访问令牌请求")
public record RefreshTokenInDTO(
        @Schema(description = "微信登录签发的刷新令牌")
        @NotBlank(message = "刷新令牌不能为空")
        @Size(max = 256, message = "刷新令牌长度不能超过256个字符")
        String refreshToken
) {
}
