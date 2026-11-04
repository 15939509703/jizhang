package com.lhj.jizhang.user.controller;

import com.lhj.jizhang.common.api.ApiResponse;
import com.lhj.jizhang.security.model.AuthenticatedUser;
import com.lhj.jizhang.user.dto.LoginUserOutDTO;
import com.lhj.jizhang.user.dto.UserProfileUpdateInDTO;
import com.lhj.jizhang.user.model.AttachmentDownload;
import com.lhj.jizhang.user.service.UserAvatarService;
import com.lhj.jizhang.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "用户资料", description = "查询和修改当前登录用户资料")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {
    private final UserProfileService userProfileService;
    private final UserAvatarService userAvatarService;

    public UserController(UserProfileService userProfileService, UserAvatarService userAvatarService) {
        this.userProfileService = userProfileService;
        this.userAvatarService = userAvatarService;
    }

    @Operation(summary = "查询当前用户资料")
    @GetMapping
    public ApiResponse<LoginUserOutDTO> get(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(userProfileService.get(user.userId()));
    }

    @Operation(summary = "修改当前用户资料")
    @PutMapping
    public ApiResponse<LoginUserOutDTO> update(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody @Valid UserProfileUpdateInDTO input
    ) {
        return ApiResponse.success(userProfileService.update(user.userId(), input));
    }

    /** 上传并替换当前用户头像。 */
    @Operation(summary = "上传当前用户头像")
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<LoginUserOutDTO> uploadAvatar(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @RequestPart("file") MultipartFile file
    ) {
        return ApiResponse.success(userAvatarService.upload(user.userId(), file));
    }

    /** 使用登录态读取当前用户头像。 */
    @Operation(summary = "读取当前用户头像")
    @GetMapping("/avatar")
    public ResponseEntity<org.springframework.core.io.Resource> downloadAvatar(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user
    ) {
        AttachmentDownload download = userAvatarService.download(user.userId());
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(download.contentType()))
                .body(download.resource());
    }
}
