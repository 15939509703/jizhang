package com.lhj.jizhang.user.controller;

import com.lhj.jizhang.common.api.ApiResponse;
import com.lhj.jizhang.security.model.AuthenticatedUser;
import com.lhj.jizhang.user.dto.ReimbursementCreateInDTO;
import com.lhj.jizhang.user.dto.ReimbursementOutDTO;
import com.lhj.jizhang.user.dto.ReimbursementReceiveInDTO;
import com.lhj.jizhang.user.dto.ReimbursementUpdateInDTO;
import com.lhj.jizhang.user.service.ReimbursementService;
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

@Tag(name = "报销管理", description = "报销记录的创建、到账和取消")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/reimbursements")
public class ReimbursementController {
    private final ReimbursementService service;
    public ReimbursementController(ReimbursementService service) { this.service = service; }

    @Operation(summary = "查询报销记录列表")
    @GetMapping
    public ApiResponse<List<ReimbursementOutDTO>> list(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
                                                       @RequestParam Long bookId,
                                                       @RequestParam(required = false) String status) {
        return ApiResponse.success(service.list(user.userId(), bookId, status));
    }

    @Operation(summary = "创建报销记录")
    @PostMapping
    public ApiResponse<ReimbursementOutDTO> create(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
                                                   @RequestBody @Valid ReimbursementCreateInDTO input) {
        return ApiResponse.success(service.create(user.userId(), input));
    }

    @Operation(summary = "查询报销记录详情")
    @GetMapping("/{id}")
    public ApiResponse<ReimbursementOutDTO> get(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
                                                @PathVariable Long id) {
        return ApiResponse.success(service.get(user.userId(), id));
    }

    @Operation(summary = "修改报销记录")
    @PutMapping("/{id}")
    public ApiResponse<ReimbursementOutDTO> update(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
                                                   @PathVariable Long id,
                                                   @RequestBody @Valid ReimbursementUpdateInDTO input) {
        return ApiResponse.success(service.update(user.userId(), id, input));
    }

    @Operation(summary = "删除报销记录")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
                                    @PathVariable Long id) {
        service.delete(user.userId(), id);
        return ApiResponse.success(null);
    }

    @Operation(summary = "确认报销到账")
    @PostMapping("/{id}/receive")
    public ApiResponse<ReimbursementOutDTO> receive(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
                                                    @PathVariable Long id,
                                                    @RequestBody @Valid ReimbursementReceiveInDTO input) {
        return ApiResponse.success(service.receive(user.userId(), id, input));
    }

    @Operation(summary = "取消报销记录")
    @PostMapping("/{id}/cancel")
    public ApiResponse<ReimbursementOutDTO> cancel(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
                                                   @PathVariable Long id) {
        return ApiResponse.success(service.cancel(user.userId(), id));
    }
}
