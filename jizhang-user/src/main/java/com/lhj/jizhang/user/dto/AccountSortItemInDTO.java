package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "AccountSortItemRequest", description = "账户排序项")
public record AccountSortItemInDTO(
        @Schema(description = "账户ID", example = "1")
        @NotNull(message = "账户ID不能为空") Long accountId,
        @Schema(description = "排序号", example = "10")
        @NotNull(message = "排序号不能为空") Integer sortNo
) {
}
