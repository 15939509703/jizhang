package com.lhj.jizhang.user.controller;

import com.lhj.jizhang.common.api.ApiResponse;
import com.lhj.jizhang.security.model.AuthenticatedUser;
import com.lhj.jizhang.user.dto.AccountCreateInDTO;
import com.lhj.jizhang.user.dto.AccountOutDTO;
import com.lhj.jizhang.user.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "账户管理", description = "资产和负债账户查询与创建")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Operation(summary = "查询账户列表", description = "查询指定账本下的全部有效账户")
    @GetMapping
    public ApiResponse<List<AccountOutDTO>> list(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "账本ID", required = true, example = "1") @RequestParam Long bookId
    ) {
        return ApiResponse.success(accountService.list(user.userId(), bookId));
    }

    @Operation(summary = "创建账户", description = "在指定账本中创建资产或负债账户")
    @PostMapping
    public ApiResponse<AccountOutDTO> create(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody @Valid AccountCreateInDTO input
    ) {
        return ApiResponse.success(accountService.create(user.userId(), input));
    }
}
