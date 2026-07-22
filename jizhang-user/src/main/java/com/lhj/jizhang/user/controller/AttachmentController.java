package com.lhj.jizhang.user.controller;

import com.lhj.jizhang.common.api.ApiResponse;
import com.lhj.jizhang.security.model.AuthenticatedUser;
import com.lhj.jizhang.user.dto.TransactionAttachmentOutDTO;
import com.lhj.jizhang.user.model.AttachmentDownload;
import com.lhj.jizhang.user.service.AttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@Tag(name = "账单附件", description = "账单图片上传、下载与删除")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1")
public class AttachmentController {
    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @Operation(summary = "上传账单图片")
    @PostMapping(value = "/transactions/{transactionId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<TransactionAttachmentOutDTO> upload(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long transactionId,
            @RequestPart("file") MultipartFile file
    ) {
        return ApiResponse.success(attachmentService.upload(user.userId(), transactionId, file));
    }

    @Operation(summary = "下载账单图片")
    @GetMapping("/attachments/{id}")
    public ResponseEntity<org.springframework.core.io.Resource> download(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id
    ) {
        AttachmentDownload download = attachmentService.download(user.userId(), id);
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(download.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(download.resource());
    }

    @Operation(summary = "删除账单图片")
    @DeleteMapping("/attachments/{id}")
    public ApiResponse<Void> delete(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id
    ) {
        attachmentService.delete(user.userId(), id);
        return ApiResponse.success(null);
    }
}
