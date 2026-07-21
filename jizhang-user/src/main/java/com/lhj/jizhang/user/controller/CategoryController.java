package com.lhj.jizhang.user.controller;

import com.lhj.jizhang.common.api.ApiResponse;
import com.lhj.jizhang.security.model.AuthenticatedUser;
import com.lhj.jizhang.user.dto.CategoryOutDTO;
import com.lhj.jizhang.user.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "分类管理", description = "账本收支分类查询")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {
    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(summary = "查询分类列表", description = "按账本和收支类型查询可用分类")
    @GetMapping
    public ApiResponse<List<CategoryOutDTO>> list(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "账本ID", required = true, example = "1") @RequestParam Long bookId,
            @Parameter(description = "分类类型", example = "EXPENSE",
                    schema = @Schema(allowableValues = {"EXPENSE", "INCOME"}))
            @RequestParam(required = false) String type
    ) {
        return ApiResponse.success(categoryService.list(user.userId(), bookId, type));
    }
}
