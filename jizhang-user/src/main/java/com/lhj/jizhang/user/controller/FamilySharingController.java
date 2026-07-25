package com.lhj.jizhang.user.controller;

import com.lhj.jizhang.common.api.ApiResponse;
import com.lhj.jizhang.security.model.AuthenticatedUser;
import com.lhj.jizhang.user.dto.BookAuditLogOutDTO;
import com.lhj.jizhang.user.dto.BookInvitationCreateInDTO;
import com.lhj.jizhang.user.dto.BookInvitationOutDTO;
import com.lhj.jizhang.user.dto.BookMemberOutDTO;
import com.lhj.jizhang.user.dto.BookMemberPermissionsInDTO;
import com.lhj.jizhang.user.service.FamilySharingService;
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

@RestController
@RequestMapping("/api/v1")
public class FamilySharingController {
    private final FamilySharingService service;
    public FamilySharingController(FamilySharingService service) { this.service = service; }

    @PostMapping("/books/{bookId}/invitations")
    public ApiResponse<BookInvitationOutDTO> create(@AuthenticationPrincipal AuthenticatedUser user,
                                                    @PathVariable Long bookId,
                                                    @RequestBody @Valid BookInvitationCreateInDTO input) {
        return ApiResponse.success(service.createInvitation(user.userId(), bookId, input));
    }

    @GetMapping("/book-invitations/preview")
    public ApiResponse<BookInvitationOutDTO> preview(@AuthenticationPrincipal AuthenticatedUser user,
                                                     @RequestParam String token) {
        return ApiResponse.success(service.preview(user.userId(), token));
    }

    @PostMapping("/book-invitations/accept")
    public ApiResponse<BookMemberOutDTO> accept(@AuthenticationPrincipal AuthenticatedUser user,
                                                @RequestParam String token) {
        return ApiResponse.success(service.accept(user.userId(), token));
    }

    @DeleteMapping("/books/{bookId}/invitations/{invitationId}")
    public ApiResponse<Void> revoke(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long bookId,
                                    @PathVariable Long invitationId) {
        service.revoke(user.userId(), bookId, invitationId);
        return ApiResponse.success(null);
    }

    @PutMapping("/books/{bookId}/members/{memberId}/permissions")
    public ApiResponse<BookMemberOutDTO> permissions(@AuthenticationPrincipal AuthenticatedUser user,
                                                     @PathVariable Long bookId, @PathVariable Long memberId,
                                                     @RequestBody @Valid BookMemberPermissionsInDTO input) {
        return ApiResponse.success(service.updatePermissions(user.userId(), bookId, memberId, input));
    }

    @GetMapping("/books/{bookId}/audit-logs")
    public ApiResponse<List<BookAuditLogOutDTO>> logs(@AuthenticationPrincipal AuthenticatedUser user,
                                                      @PathVariable Long bookId) {
        return ApiResponse.success(service.auditLogs(user.userId(), bookId));
    }
}
