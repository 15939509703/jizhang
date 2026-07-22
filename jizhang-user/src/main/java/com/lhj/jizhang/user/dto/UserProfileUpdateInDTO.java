package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "UserProfileUpdateRequest", description = "修改当前用户资料请求")
public record UserProfileUpdateInDTO(
        @Schema(description = "用户昵称", example = "小明")
        @NotBlank(message = "昵称不能为空")
        @Size(max = 64, message = "昵称长度不能超过64个字符")
        String nickName
) {
}
