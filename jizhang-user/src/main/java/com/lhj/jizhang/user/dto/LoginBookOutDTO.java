package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "LoginBook", description = "登录后的默认账本")
public record LoginBookOutDTO(
        @Schema(description = "账本ID", example = "1")
        Long id,
        @Schema(description = "账本编号", example = "BOOK202607210001")
        String bookNo,
        @Schema(description = "账本名称", example = "默认账本")
        String name
) {
}
