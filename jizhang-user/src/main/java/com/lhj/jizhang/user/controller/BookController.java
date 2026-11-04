package com.lhj.jizhang.user.controller;

import com.lhj.jizhang.common.api.ApiResponse;
import com.lhj.jizhang.security.model.AuthenticatedUser;
import com.lhj.jizhang.user.dto.BookCreateInDTO;
import com.lhj.jizhang.user.dto.BookMemberInviteInDTO;
import com.lhj.jizhang.user.dto.BookMemberOutDTO;
import com.lhj.jizhang.user.dto.BookMemberRoleInDTO;
import com.lhj.jizhang.user.dto.BookOutDTO;
import com.lhj.jizhang.user.dto.BookUpdateInDTO;
import com.lhj.jizhang.user.model.AttachmentDownload;
import com.lhj.jizhang.user.service.BookCoverService;
import com.lhj.jizhang.user.service.BookMemberService;
import com.lhj.jizhang.user.service.BookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "账本管理", description = "账本资料、切换和成员权限管理")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/books")
public class BookController {
    private final BookService bookService;
    private final BookMemberService bookMemberService;
    private final BookCoverService bookCoverService;

    public BookController(BookService bookService, BookMemberService bookMemberService,
                          BookCoverService bookCoverService) {
        this.bookService = bookService;
        this.bookMemberService = bookMemberService;
        this.bookCoverService = bookCoverService;
    }

    @Operation(summary = "查询账本列表")
    @GetMapping
    public ApiResponse<List<BookOutDTO>> list(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(bookService.list(user.userId()));
    }

    @Operation(summary = "创建账本")
    @PostMapping
    public ApiResponse<BookOutDTO> create(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody @Valid BookCreateInDTO input
    ) {
        return ApiResponse.success(bookService.create(user.userId(), input));
    }

    @Operation(summary = "修改账本资料")
    @PutMapping("/{id}")
    public ApiResponse<BookOutDTO> update(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @RequestBody @Valid BookUpdateInDTO input
    ) {
        return ApiResponse.success(bookService.update(user.userId(), id, input));
    }

    /** 上传账本封面，仅所有者和管理员可操作。 */
    @Operation(summary = "上传账本封面")
    @PostMapping(value = "/{id}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<BookOutDTO> uploadCover(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file
    ) {
        return ApiResponse.success(bookCoverService.upload(user.userId(), id, file));
    }

    /** 使用登录态读取账本封面，仅账本成员可访问。 */
    @Operation(summary = "读取账本封面")
    @GetMapping("/{id}/cover")
    public ResponseEntity<org.springframework.core.io.Resource> downloadCover(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id
    ) {
        AttachmentDownload download = bookCoverService.download(user.userId(), id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(download.contentType()))
                .body(download.resource());
    }

    @Operation(summary = "查询账本成员")
    @GetMapping("/{id}/members")
    public ApiResponse<List<BookMemberOutDTO>> members(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id
    ) {
        return ApiResponse.success(bookMemberService.list(user.userId(), id));
    }

    @Operation(summary = "邀请账本成员")
    @PostMapping("/{id}/members")
    public ApiResponse<BookMemberOutDTO> invite(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @RequestBody @Valid BookMemberInviteInDTO input
    ) {
        return ApiResponse.success(bookMemberService.invite(user.userId(), id, input));
    }

    @Operation(summary = "修改成员角色")
    @PutMapping("/{bookId}/members/{memberId}")
    public ApiResponse<BookMemberOutDTO> updateMember(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long bookId,
            @PathVariable Long memberId,
            @RequestBody @Valid BookMemberRoleInDTO input
    ) {
        return ApiResponse.success(bookMemberService.updateRole(user.userId(), bookId, memberId, input));
    }

    @Operation(summary = "移除账本成员")
    @DeleteMapping("/{bookId}/members/{memberId}")
    public ApiResponse<Void> removeMember(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long bookId,
            @PathVariable Long memberId
    ) {
        bookMemberService.remove(user.userId(), bookId, memberId);
        return ApiResponse.success(null);
    }
}
