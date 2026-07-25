package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "AccountUpdateRequest", description = "修改账户请求")
public record AccountUpdateInDTO(
        @Schema(description = "账户名称", example = "微信零钱")
        @NotBlank(message = "账户名称不能为空")
        @Size(max = 64, message = "账户名称长度不能超过64个字符")
        String name,
        @Schema(description = "是否计入资产统计；不传时保持原值")
        Boolean includedInAssets
) {
    public AccountUpdateInDTO(String name) {
        this(name, null);
    }
}
