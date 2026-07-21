package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "BookCreateRequest", description = "创建账本请求")
public record BookCreateInDTO(
        @Schema(description = "账本名称", example = "日常账本")
        @NotBlank(message = "账本名称不能为空")
        @Size(max = 64, message = "账本名称长度不能超过64个字符")
        String name,
        @Schema(description = "账本说明", example = "记录家庭日常收支")
        @Size(max = 500, message = "账本说明长度不能超过500个字符")
        String description,
        @Schema(description = "ISO 4217币种代码", example = "CNY")
        @Pattern(regexp = "^[A-Z]{3,8}$", message = "币种代码格式不正确")
        String currencyCode,
        @Schema(description = "IANA时区", example = "Asia/Shanghai")
        @Size(max = 64, message = "时区长度不能超过64个字符")
        String timezone
) {
}
