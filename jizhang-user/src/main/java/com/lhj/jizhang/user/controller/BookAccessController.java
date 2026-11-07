package com.lhj.jizhang.user.controller;

import com.lhj.jizhang.common.api.ApiResponse;
import com.lhj.jizhang.security.model.AuthenticatedUser;
import com.lhj.jizhang.user.dto.BookAccessOutDTO;
import com.lhj.jizhang.user.dto.BookPolicyUpdateInDTO;
import com.lhj.jizhang.user.service.BookAccessService;
import com.lhj.jizhang.user.service.BookPolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "账本权限", description = "当前权限与成员策略整体保存")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/books/{bookId}")
public class BookAccessController {
    private final BookAccessService accessService;
    private final BookPolicyService policyService;

    public BookAccessController(BookAccessService accessService, BookPolicyService policyService) {
        this.accessService = accessService;
        this.policyService = policyService;
    }

    /** 当前用户按账本实时计算的有效权限。 */
    @Operation(summary = "查询我的账本权限")
    @GetMapping("/my-access")
    public ApiResponse<BookAccessOutDTO> myAccess(@AuthenticationPrincipal AuthenticatedUser user,
                                                   @PathVariable Long bookId) {
        return ApiResponse.success(accessService.myAccess(user.userId(), bookId));
    }

    /** 管理员可读取其有权管理的成员策略。 */
    @Operation(summary = "查询成员权限")
    @GetMapping("/members/{memberId}/access")
    public ApiResponse<BookAccessOutDTO> memberAccess(@AuthenticationPrincipal AuthenticatedUser user,
                                                       @PathVariable Long bookId, @PathVariable Long memberId) {
        return ApiResponse.success(policyService.get(user.userId(), bookId, memberId));
    }

    /** 以版本号原子保存角色、菜单、动作和账单范围。 */
    @Operation(summary = "整体保存成员权限")
    @PutMapping("/members/{memberId}/access")
    public ApiResponse<BookAccessOutDTO> update(@AuthenticationPrincipal AuthenticatedUser user,
                                                 @PathVariable Long bookId, @PathVariable Long memberId,
                                                 @RequestBody @Valid BookPolicyUpdateInDTO input) {
        return ApiResponse.success(policyService.update(user.userId(), bookId, memberId, input));
    }
}
