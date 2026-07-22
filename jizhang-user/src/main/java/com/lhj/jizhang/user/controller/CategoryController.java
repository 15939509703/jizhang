package com.lhj.jizhang.user.controller;

import com.lhj.jizhang.common.api.ApiResponse;
import com.lhj.jizhang.security.model.AuthenticatedUser;
import com.lhj.jizhang.user.dto.CategoryCreateInDTO;
import com.lhj.jizhang.user.dto.CategoryOutDTO;
import com.lhj.jizhang.user.dto.CategorySortInDTO;
import com.lhj.jizhang.user.dto.CategoryUpdateInDTO;
import com.lhj.jizhang.user.dto.CategoryVisibilityInDTO;
import com.lhj.jizhang.user.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "分类管理", description = "账本收支分类查询与维护")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {
    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(summary = "查询分类列表")
    @GetMapping
    public ApiResponse<List<CategoryOutDTO>> list(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam Long bookId,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "false") boolean includeHidden
    ) {
        return ApiResponse.success(categoryService.list(user.userId(), bookId, type, includeHidden));
    }

    @Operation(summary = "新增自定义分类")
    @PostMapping
    public ApiResponse<CategoryOutDTO> create(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody @Valid CategoryCreateInDTO input
    ) {
        return ApiResponse.success(categoryService.create(user.userId(), input));
    }

    @Operation(summary = "修改分类")
    @PutMapping("/{id}")
    public ApiResponse<CategoryOutDTO> update(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @RequestBody @Valid CategoryUpdateInDTO input
    ) {
        return ApiResponse.success(categoryService.update(user.userId(), id, input));
    }

    @Operation(summary = "隐藏或恢复分类")
    @PutMapping("/{id}/visibility")
    public ApiResponse<CategoryOutDTO> setVisibility(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @RequestBody @Valid CategoryVisibilityInDTO input
    ) {
        return ApiResponse.success(categoryService.setHidden(user.userId(), id, input.hidden()));
    }

    @Operation(summary = "调整分类排序")
    @PutMapping("/sort")
    public ApiResponse<Void> sort(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody @Valid CategorySortInDTO input
    ) {
        categoryService.sort(user.userId(), input);
        return ApiResponse.success(null);
    }

    @Operation(summary = "删除分类")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id
    ) {
        categoryService.delete(user.userId(), id);
        return ApiResponse.success(null);
    }
}
