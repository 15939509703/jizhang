package com.lhj.jizhang.user.controller;

import com.lhj.jizhang.common.api.ApiResponse;
import com.lhj.jizhang.security.model.AuthenticatedUser;
import com.lhj.jizhang.user.dto.BookAuditLogOutDTO;
import com.lhj.jizhang.user.dto.BookInvitationCreateInDTO;
import com.lhj.jizhang.user.dto.BookInvitationOutDTO;
import com.lhj.jizhang.user.dto.BookInvitationTokenInDTO;
import com.lhj.jizhang.user.dto.BookMemberOutDTO;
import com.lhj.jizhang.user.dto.BookMemberPermissionsInDTO;
import com.lhj.jizhang.user.service.FamilySharingService;
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

@Tag(name = "家庭共享", description = "账本邀请、成员权限和审计日志")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1")
public class FamilySharingController {
    private final FamilySharingService service;
    public FamilySharingController(FamilySharingService service) { this.service = service; }

    @Operation(summary = "创建账本邀请")
    @PostMapping("/books/{bookId}/invitations")
    public ApiResponse<BookInvitationOutDTO> create(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
                                                    @PathVariable Long bookId,
                                                    @RequestBody @Valid BookInvitationCreateInDTO input) {
        return ApiResponse.success(service.createInvitation(user.userId(), bookId, input));
    }

    @Operation(summary = "预览账本邀请")
    @GetMapping("/book-invitations/preview")
    public ApiResponse<BookInvitationOutDTO> preview(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
                                                     @RequestParam String token) {
        return ApiResponse.success(service.preview(user.userId(), token));
    }

    /** 从请求体预览邀请码，供原生 iOS 应用使用。 */
    @Operation(summary = "使用邀请码预览账本邀请")
    @PostMapping("/book-invitations/preview")
    public ApiResponse<BookInvitationOutDTO> previewByCode(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
                                                             @RequestBody @Valid BookInvitationTokenInDTO input) {
        return ApiResponse.success(service.preview(user.userId(), input.token()));
    }

    @Operation(summary = "接受账本邀请")
    @PostMapping("/book-invitations/accept")
    public ApiResponse<BookMemberOutDTO> accept(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
                                                @RequestParam String token) {
        return ApiResponse.success(service.accept(user.userId(), token));
    }

    /** 从请求体接受邀请码，不在 URL 中传递凭证。 */
    @Operation(summary = "使用邀请码接受账本邀请")
    @PostMapping("/book-invitations/accept-by-code")
    public ApiResponse<BookMemberOutDTO> acceptByCode(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
                                                       @RequestBody @Valid BookInvitationTokenInDTO input) {
        return ApiResponse.success(service.accept(user.userId(), input.token()));
    }

    @Operation(summary = "撤销账本邀请")
    @DeleteMapping("/books/{bookId}/invitations/{invitationId}")
    public ApiResponse<Void> revoke(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long bookId,
                                    @PathVariable Long invitationId) {
        service.revoke(user.userId(), bookId, invitationId);
        return ApiResponse.success(null);
    }

    @Operation(summary = "修改账本成员权限")
    @PutMapping("/books/{bookId}/members/{memberId}/permissions")
    public ApiResponse<BookMemberOutDTO> permissions(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
                                                     @PathVariable Long bookId, @PathVariable Long memberId,
                                                     @RequestBody @Valid BookMemberPermissionsInDTO input) {
        return ApiResponse.success(service.updatePermissions(user.userId(), bookId, memberId, input));
    }

    @Operation(summary = "查询账本权限审计日志")
    @GetMapping("/books/{bookId}/audit-logs")
    public ApiResponse<List<BookAuditLogOutDTO>> logs(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
                                                      @PathVariable Long bookId) {
        return ApiResponse.success(service.auditLogs(user.userId(), bookId));
    }
}
