package com.lhj.jizhang.user.controller;

import com.lhj.jizhang.common.api.ApiResponse;
import com.lhj.jizhang.security.model.AuthenticatedUser;
import com.lhj.jizhang.user.dto.SyncChangesOutDTO;
import com.lhj.jizhang.user.service.SyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@Tag(name = "数据同步", description = "弱网重试和增量同步")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {
    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @Operation(summary = "查询增量变更")
    @GetMapping("/changes")
    public ApiResponse<SyncChangesOutDTO> changes(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "账本ID", required = true, example = "1") @RequestParam Long bookId,
            @Parameter(description = "上次同步游标，ISO-8601格式") @RequestParam(required = false) Instant cursor
    ) {
        return ApiResponse.success(syncService.changes(user.userId(), bookId, cursor));
    }
}
