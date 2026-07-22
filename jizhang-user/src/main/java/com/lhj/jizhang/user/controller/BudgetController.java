package com.lhj.jizhang.user.controller;

import com.lhj.jizhang.common.api.ApiResponse;
import com.lhj.jizhang.security.model.AuthenticatedUser;
import com.lhj.jizhang.user.dto.BudgetOutDTO;
import com.lhj.jizhang.user.dto.BudgetSaveInDTO;
import com.lhj.jizhang.user.service.BudgetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

@Tag(name = "预算管理", description = "月度预算和分类预算查询与保存")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/budgets")
public class BudgetController {
    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @Operation(summary = "查询月度预算", description = "查询指定账本自然月的总预算、分类预算和实时使用进度")
    @GetMapping
    public ApiResponse<BudgetOutDTO> getBudget(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "账本ID", required = true, example = "1") @RequestParam Long bookId,
            @Parameter(description = "预算月份，默认当前月", example = "2026-07")
            @DateTimeFormat(pattern = "yyyy-MM") @RequestParam(required = false) YearMonth month
    ) {
        return ApiResponse.success(budgetService.getBudget(user.userId(), bookId, month));
    }

    @Operation(summary = "创建或修改预算", description = "按账本和自然月创建或覆盖总预算与分类预算")
    @PostMapping
    public ApiResponse<BudgetOutDTO> saveBudget(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody @Valid BudgetSaveInDTO input
    ) {
        return ApiResponse.success(budgetService.saveBudget(user.userId(), input));
    }
}
