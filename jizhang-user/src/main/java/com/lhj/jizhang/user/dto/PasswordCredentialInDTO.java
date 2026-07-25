package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "PasswordCredentialRequest", description = "网页登录凭证")
public record PasswordCredentialInDTO(
        @Schema(description = "网页登录账号", example = "my-account")
        @NotBlank(message = "登录账号不能为空")
        @Size(min = 3, max = 64, message = "登录账号长度需为3到64个字符")
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{2,63}", message = "登录账号只能包含字母、数字、点、下划线和短横线")
        String username,
        @Schema(description = "登录密码")
        @NotBlank(message = "登录密码不能为空")
        @Size(min = 8, max = 72, message = "登录密码长度需为8到72个字符")
        String password
) {
}
