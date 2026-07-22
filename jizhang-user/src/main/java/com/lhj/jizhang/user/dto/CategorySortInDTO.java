package com.lhj.jizhang.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CategorySortInDTO(
        @NotNull(message = "账本ID不能为空") Long bookId,
        @NotEmpty(message = "排序列表不能为空") List<@Valid CategorySortItemInDTO> items
) {
}
