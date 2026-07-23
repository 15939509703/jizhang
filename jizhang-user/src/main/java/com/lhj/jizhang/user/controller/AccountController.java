package com.lhj.jizhang.user.controller;

import com.lhj.jizhang.common.api.ApiResponse;
import com.lhj.jizhang.security.model.AuthenticatedUser;
import com.lhj.jizhang.user.dto.AccountAdjustInDTO;
import com.lhj.jizhang.user.dto.AccountCreateInDTO;
import com.lhj.jizhang.user.dto.AccountEntryPageOutDTO;
import com.lhj.jizhang.user.dto.AccountOutDTO;
import com.lhj.jizhang.user.dto.AccountSortInDTO;
import com.lhj.jizhang.user.dto.AccountUpdateInDTO;
import com.lhj.jizhang.user.service.AccountService;
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

@Tag(name = "账户管理", description = "账户、余额调整与账户明细")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Operation(summary = "查询账户列表")
    @GetMapping
    public ApiResponse<List<AccountOutDTO>> list(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam Long bookId
    ) {
        return ApiResponse.success(accountService.list(user.userId(), bookId));
    }

    @Operation(summary = "查询账户详情")
    @GetMapping("/{id}")
    public ApiResponse<AccountOutDTO> get(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id
    ) {
        return ApiResponse.success(accountService.get(user.userId(), id));
    }

    @Operation(summary = "创建账户")
    @PostMapping
    public ApiResponse<AccountOutDTO> create(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody @Valid AccountCreateInDTO input
    ) {
        return ApiResponse.success(accountService.create(user.userId(), input));
    }

    @Operation(summary = "修改账户")
    @PutMapping("/{id}")
    public ApiResponse<AccountOutDTO> update(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @RequestBody @Valid AccountUpdateInDTO input
    ) {
        return ApiResponse.success(accountService.update(user.userId(), id, input));
    }

    @Operation(summary = "调整账户排序")
    @PutMapping("/sort")
    public ApiResponse<Void> sort(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody @Valid AccountSortInDTO input
    ) {
        accountService.sort(user.userId(), input);
        return ApiResponse.success(null);
    }

    @Operation(summary = "删除账户")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id
    ) {
        accountService.delete(user.userId(), id);
        return ApiResponse.success(null);
    }

    @Operation(summary = "调整账户余额")
    @PostMapping("/{id}/adjustments")
    public ApiResponse<AccountOutDTO> adjust(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @RequestBody @Valid AccountAdjustInDTO input
    ) {
        return ApiResponse.success(accountService.adjust(user.userId(), id, input));
    }

    @Operation(summary = "查询账户余额明细")
    @GetMapping("/{id}/entries")
    public ApiResponse<AccountEntryPageOutDTO> entries(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @RequestParam(required = false) Long cursorId,
            @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.success(accountService.entries(user.userId(), id, cursorId, limit));
    }
}
