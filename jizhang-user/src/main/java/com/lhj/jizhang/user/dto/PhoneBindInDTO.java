package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "PhoneBindRequest", description = "微信账号绑定手机号登录")
public record PhoneBindInDTO(
        @Schema(description = "中国大陆手机号", example = "13800138000")
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone,
        @Schema(description = "登录密码")
        @NotBlank(message = "登录密码不能为空")
        @Size(min = 8, max = 72, message = "登录密码长度需为8到72个字符")
        String password
) {
}
