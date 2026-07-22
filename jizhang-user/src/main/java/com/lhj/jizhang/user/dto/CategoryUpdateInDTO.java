package com.lhj.jizhang.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryUpdateInDTO(
        @NotBlank(message = "分类名称不能为空")
        @Size(max = 64, message = "分类名称长度不能超过64个字符") String name,
        @Size(max = 64, message = "图标标识长度不能超过64个字符") String icon,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "颜色必须为十六进制色值") String color
) {
}
