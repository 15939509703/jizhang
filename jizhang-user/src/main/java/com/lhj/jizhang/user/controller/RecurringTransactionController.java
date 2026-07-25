package com.lhj.jizhang.user.controller;

import com.lhj.jizhang.common.api.ApiResponse;
import com.lhj.jizhang.security.model.AuthenticatedUser;
import com.lhj.jizhang.user.dto.RecurringConfirmInDTO;
import com.lhj.jizhang.user.dto.RecurringExecutionOutDTO;
import com.lhj.jizhang.user.dto.RecurringRuleCreateInDTO;
import com.lhj.jizhang.user.dto.RecurringRuleOutDTO;
import com.lhj.jizhang.user.dto.RecurringRuleUpdateInDTO;
import com.lhj.jizhang.user.service.RecurringExecutionService;
import com.lhj.jizhang.user.service.RecurringRuleService;
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

@Tag(name = "周期账单", description = "周期规则和待确认执行项管理")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1")
public class RecurringTransactionController {
    private final RecurringRuleService ruleService;
    private final RecurringExecutionService executionService;

    public RecurringTransactionController(RecurringRuleService ruleService,
                                          RecurringExecutionService executionService) {
        this.ruleService = ruleService;
        this.executionService = executionService;
    }

    @Operation(summary = "查询周期规则列表")
    @GetMapping("/recurring-transactions")
    public ApiResponse<List<RecurringRuleOutDTO>> listRules(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam Long bookId,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.success(ruleService.list(user.userId(), bookId, status));
    }

    @Operation(summary = "创建周期规则")
    @PostMapping("/recurring-transactions")
    public ApiResponse<RecurringRuleOutDTO> createRule(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody @Valid RecurringRuleCreateInDTO input
    ) {
        return ApiResponse.success(ruleService.create(user.userId(), input));
    }

    @Operation(summary = "查询周期规则详情")
    @GetMapping("/recurring-transactions/{id}")
    public ApiResponse<RecurringRuleOutDTO> getRule(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id
    ) {
        return ApiResponse.success(ruleService.get(user.userId(), id));
    }

    @Operation(summary = "修改周期规则")
    @PutMapping("/recurring-transactions/{id}")
    public ApiResponse<RecurringRuleOutDTO> updateRule(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @RequestBody @Valid RecurringRuleUpdateInDTO input
    ) {
        return ApiResponse.success(ruleService.update(user.userId(), id, input));
    }

    @Operation(summary = "暂停周期规则")
    @PostMapping("/recurring-transactions/{id}/pause")
    public ApiResponse<RecurringRuleOutDTO> pauseRule(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return ApiResponse.success(ruleService.pause(user.userId(), id));
    }

    @Operation(summary = "恢复周期规则")
    @PostMapping("/recurring-transactions/{id}/resume")
    public ApiResponse<RecurringRuleOutDTO> resumeRule(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return ApiResponse.success(ruleService.resume(user.userId(), id));
    }

    @Operation(summary = "立即执行下一期周期规则")
    @PostMapping("/recurring-transactions/{id}/execute")
    public ApiResponse<RecurringExecutionOutDTO> executeRule(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return ApiResponse.success(executionService.executeNow(user.userId(), id));
    }

    @Operation(summary = "删除周期规则")
    @DeleteMapping("/recurring-transactions/{id}")
    public ApiResponse<Void> deleteRule(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        ruleService.delete(user.userId(), id);
        return ApiResponse.success(null);
    }

    @Operation(summary = "查询周期执行记录")
    @GetMapping("/recurring-executions")
    public ApiResponse<List<RecurringExecutionOutDTO>> listExecutions(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam Long bookId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.success(executionService.list(user.userId(), bookId, status, limit));
    }

    @Operation(summary = "查询待确认周期账单数量")
    @GetMapping("/recurring-executions/pending-count")
    public ApiResponse<Long> pendingCount(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user, @RequestParam Long bookId) {
        return ApiResponse.success(executionService.pendingCount(user.userId(), bookId));
    }

    @Operation(summary = "查询周期执行详情")
    @GetMapping("/recurring-executions/{id}")
    public ApiResponse<RecurringExecutionOutDTO> getExecution(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return ApiResponse.success(executionService.get(user.userId(), id));
    }

    @Operation(summary = "确认周期执行项")
    @PostMapping("/recurring-executions/{id}/confirm")
    public ApiResponse<RecurringExecutionOutDTO> confirm(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @RequestBody @Valid RecurringConfirmInDTO input
    ) {
        return ApiResponse.success(executionService.confirm(user.userId(), id, input));
    }

    @Operation(summary = "跳过周期执行项")
    @PostMapping("/recurring-executions/{id}/skip")
    public ApiResponse<RecurringExecutionOutDTO> skip(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return ApiResponse.success(executionService.skip(user.userId(), id));
    }

    @Operation(summary = "重试失败的周期执行项")
    @PostMapping("/recurring-executions/{id}/retry")
    public ApiResponse<RecurringExecutionOutDTO> retry(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return ApiResponse.success(executionService.retry(user.userId(), id));
    }
}
