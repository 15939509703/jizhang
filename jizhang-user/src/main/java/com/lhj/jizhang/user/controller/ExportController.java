package com.lhj.jizhang.user.controller;

import com.lhj.jizhang.common.api.ApiResponse;
import com.lhj.jizhang.security.model.AuthenticatedUser;
import com.lhj.jizhang.user.dto.ExportCreateInDTO;
import com.lhj.jizhang.user.dto.ExportTaskOutDTO;
import com.lhj.jizhang.user.service.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@Tag(name = "数据导出", description = "账单明细 Excel/CSV 导出")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/exports")
public class ExportController {
    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @Operation(summary = "创建导出任务")
    @PostMapping
    public ApiResponse<ExportTaskOutDTO> create(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody @Valid ExportCreateInDTO input
    ) {
        return ApiResponse.success(exportService.create(user.userId(), input));
    }

    @Operation(summary = "查询导出任务")
    @GetMapping("/{id}")
    public ApiResponse<ExportTaskOutDTO> get(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id
    ) {
        return ApiResponse.success(exportService.get(user.userId(), id));
    }

    @Operation(summary = "下载导出文件")
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id
    ) {
        ExportService.ExportFile file = exportService.download(user.userId(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(file.bytes());
    }
}
