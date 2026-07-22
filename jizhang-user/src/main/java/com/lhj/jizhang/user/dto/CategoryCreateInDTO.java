package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "CategoryCreateRequest", description = "创建分类请求")
public record CategoryCreateInDTO(
        @NotNull(message = "账本ID不能为空") Long bookId,
        @NotBlank(message = "分类类型不能为空")
        @Pattern(regexp = "EXPENSE|INCOME", message = "分类类型不正确") String categoryType,
        @NotBlank(message = "分类名称不能为空")
        @Size(max = 64, message = "分类名称长度不能超过64个字符") String name,
        @Size(max = 64, message = "图标标识长度不能超过64个字符") String icon,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "颜色必须为十六进制色值") String color
) {
}
