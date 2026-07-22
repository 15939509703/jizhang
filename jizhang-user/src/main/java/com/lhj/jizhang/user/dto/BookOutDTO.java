package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "Book", description = "账本信息")
public record BookOutDTO(
        @Schema(description = "账本ID", example = "1")
        Long id,
        @Schema(description = "账本编号", example = "BOOK202607210001")
        String bookNo,
        @Schema(description = "账本名称", example = "日常账本")
        String name,
        @Schema(description = "账本说明", example = "记录家庭日常收支")
        String description,
        @Schema(description = "账本封面地址", example = "https://example.com/book-cover.jpg")
        String coverUrl,
        @Schema(description = "币种代码", example = "CNY")
        String currencyCode,
        @Schema(description = "账本时区", example = "Asia/Shanghai")
        String timezone,
        @Schema(description = "当前用户在账本中的角色", example = "OWNER")
        String role
) {
}
