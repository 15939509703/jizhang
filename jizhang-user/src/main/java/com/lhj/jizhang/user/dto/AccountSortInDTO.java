package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(name = "AccountSortRequest", description = "账户排序请求")
public record AccountSortInDTO(
        @Schema(description = "账本ID", example = "1")
        @NotNull(message = "账本ID不能为空") Long bookId,
        @Schema(description = "排序项列表")
        @NotEmpty(message = "排序列表不能为空") List<@Valid AccountSortItemInDTO> items
) {
}
