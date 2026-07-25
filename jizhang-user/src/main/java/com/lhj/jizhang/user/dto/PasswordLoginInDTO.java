package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "PasswordLoginRequest", description = "网页登录")
public record PasswordLoginInDTO(
        @Schema(description = "网页登录账号", example = "my-account")
        @NotBlank(message = "登录账号不能为空")
        @Size(max = 64, message = "登录账号长度不能超过64个字符")
        String username,
        @Schema(description = "登录密码")
        @NotBlank(message = "登录密码不能为空")
        @Size(max = 72, message = "登录密码长度不能超过72个字符")
        String password
) {
}
