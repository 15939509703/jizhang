package com.lhj.jizhang.user.dto;

import jakarta.validation.constraints.NotNull;

public record CategorySortItemInDTO(
        @NotNull(message = "分类ID不能为空") Long categoryId,
        @NotNull(message = "排序号不能为空") Integer sortNo
) {
}
