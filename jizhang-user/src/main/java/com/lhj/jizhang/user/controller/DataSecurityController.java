package com.lhj.jizhang.user.controller;

import com.lhj.jizhang.common.api.ApiResponse;
import com.lhj.jizhang.security.model.AuthenticatedUser;
import com.lhj.jizhang.user.dto.BackupTaskOutDTO;
import com.lhj.jizhang.user.dto.DuplicateFindingOutDTO;
import com.lhj.jizhang.user.dto.RestoreCheckOutDTO;
import com.lhj.jizhang.user.service.DataSecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "数据安全", description = "账本备份、恢复检查和重复账单处理")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/data-security")
public class DataSecurityController {
    private final DataSecurityService service;
    public DataSecurityController(DataSecurityService service) { this.service = service; }

    @Operation(summary = "查询账本备份列表")
    @GetMapping("/backups")
    public ApiResponse<List<BackupTaskOutDTO>> backups(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
                                                       @RequestParam Long bookId) {
        return ApiResponse.success(service.backups(user.userId(), bookId));
    }

    @Operation(summary = "创建账本备份")
    @PostMapping("/backups")
    public ApiResponse<BackupTaskOutDTO> create(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
                                                @RequestParam Long bookId) {
        return ApiResponse.success(service.createBackup(user.userId(), bookId));
    }

    @Operation(summary = "下载账本备份文件")
    @GetMapping("/backups/{id}/download")
    public ResponseEntity<byte[]> download(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        DataSecurityService.BackupFile file = service.download(user.userId(), id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .body(file.bytes());
    }

    @Operation(summary = "检查备份恢复结果")
    @PostMapping("/backups/{id}/restore-check")
    public ApiResponse<RestoreCheckOutDTO> check(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return ApiResponse.success(service.checkRestore(user.userId(), id));
    }

    @Operation(summary = "按检查结果恢复备份")
    @PostMapping("/restore-checks/{id}/restore")
    public ApiResponse<RestoreCheckOutDTO> restore(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return ApiResponse.success(service.restore(user.userId(), id));
    }

    @Operation(summary = "查询疑似重复账单")
    @GetMapping("/duplicates")
    public ApiResponse<List<DuplicateFindingOutDTO>> findings(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
                                                              @RequestParam Long bookId) {
        return ApiResponse.success(service.findings(user.userId(), bookId));
    }

    @Operation(summary = "扫描疑似重复账单")
    @PostMapping("/duplicates/scan")
    public ApiResponse<List<DuplicateFindingOutDTO>> scan(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
                                                          @RequestParam Long bookId) {
        return ApiResponse.success(service.scanDuplicates(user.userId(), bookId));
    }

    @Operation(summary = "确认或忽略重复账单")
    @PostMapping("/duplicates/{id}/handle")
    public ApiResponse<DuplicateFindingOutDTO> handle(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
                                                      @PathVariable Long id,
                                                      @RequestParam boolean confirmDuplicate) {
        return ApiResponse.success(service.handleFinding(user.userId(), id, confirmDuplicate));
    }
}
