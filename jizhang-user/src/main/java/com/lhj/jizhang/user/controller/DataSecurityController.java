package com.lhj.jizhang.user.controller;

import com.lhj.jizhang.common.api.ApiResponse;
import com.lhj.jizhang.security.model.AuthenticatedUser;
import com.lhj.jizhang.user.dto.BackupTaskOutDTO;
import com.lhj.jizhang.user.dto.DuplicateFindingOutDTO;
import com.lhj.jizhang.user.dto.RestoreCheckOutDTO;
import com.lhj.jizhang.user.service.DataSecurityService;
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

@RestController
@RequestMapping("/api/v1/data-security")
public class DataSecurityController {
    private final DataSecurityService service;
    public DataSecurityController(DataSecurityService service) { this.service = service; }

    @GetMapping("/backups")
    public ApiResponse<List<BackupTaskOutDTO>> backups(@AuthenticationPrincipal AuthenticatedUser user,
                                                       @RequestParam Long bookId) {
        return ApiResponse.success(service.backups(user.userId(), bookId));
    }

    @PostMapping("/backups")
    public ApiResponse<BackupTaskOutDTO> create(@AuthenticationPrincipal AuthenticatedUser user,
                                                @RequestParam Long bookId) {
        return ApiResponse.success(service.createBackup(user.userId(), bookId));
    }

    @GetMapping("/backups/{id}/download")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        DataSecurityService.BackupFile file = service.download(user.userId(), id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .body(file.bytes());
    }

    @PostMapping("/backups/{id}/restore-check")
    public ApiResponse<RestoreCheckOutDTO> check(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return ApiResponse.success(service.checkRestore(user.userId(), id));
    }

    @PostMapping("/restore-checks/{id}/restore")
    public ApiResponse<RestoreCheckOutDTO> restore(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return ApiResponse.success(service.restore(user.userId(), id));
    }

    @GetMapping("/duplicates")
    public ApiResponse<List<DuplicateFindingOutDTO>> findings(@AuthenticationPrincipal AuthenticatedUser user,
                                                              @RequestParam Long bookId) {
        return ApiResponse.success(service.findings(user.userId(), bookId));
    }

    @PostMapping("/duplicates/scan")
    public ApiResponse<List<DuplicateFindingOutDTO>> scan(@AuthenticationPrincipal AuthenticatedUser user,
                                                          @RequestParam Long bookId) {
        return ApiResponse.success(service.scanDuplicates(user.userId(), bookId));
    }

    @PostMapping("/duplicates/{id}/handle")
    public ApiResponse<DuplicateFindingOutDTO> handle(@AuthenticationPrincipal AuthenticatedUser user,
                                                      @PathVariable Long id,
                                                      @RequestParam boolean confirmDuplicate) {
        return ApiResponse.success(service.handleFinding(user.userId(), id, confirmDuplicate));
    }
}
