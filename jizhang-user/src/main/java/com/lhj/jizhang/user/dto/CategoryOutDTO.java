package com.lhj.jizhang.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "Category", description = "收支分类")
public record CategoryOutDTO(
        @Schema(description = "分类ID", example = "1")
        Long id,
        @Schema(description = "分类编号", example = "CAT_FOOD")
        String categoryNo,
        @Schema(description = "分类类型", example = "EXPENSE", allowableValues = {"EXPENSE", "INCOME"})
        String categoryType,
        @Schema(description = "父分类ID", example = "10")
        Long parentId,
        @Schema(description = "分类名称", example = "餐饮")
        String name,
        @Schema(description = "图标标识", example = "utensils")
        String icon,
        @Schema(description = "展示颜色", example = "#FF6B35")
        String color,
        @Schema(description = "排序号", example = "10")
        Integer sortNo,
        @Schema(description = "是否隐藏", example = "false")
        boolean hidden,
        @Schema(description = "是否系统预置分类", example = "false")
        boolean system
) {
}
