package com.lhj.jizhang.user.controller;

import com.lhj.jizhang.common.api.ApiResponse;
import com.lhj.jizhang.security.model.AuthenticatedUser;
import com.lhj.jizhang.user.dto.ReimbursementCreateInDTO;
import com.lhj.jizhang.user.dto.ReimbursementOutDTO;
import com.lhj.jizhang.user.dto.ReimbursementReceiveInDTO;
import com.lhj.jizhang.user.dto.ReimbursementUpdateInDTO;
import com.lhj.jizhang.user.service.ReimbursementService;
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
@RequestMapping("/api/v1/reimbursements")
public class ReimbursementController {
    private final ReimbursementService service;
    public ReimbursementController(ReimbursementService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<ReimbursementOutDTO>> list(@AuthenticationPrincipal AuthenticatedUser user,
                                                       @RequestParam Long bookId,
                                                       @RequestParam(required = false) String status) {
        return ApiResponse.success(service.list(user.userId(), bookId, status));
    }

    @PostMapping
    public ApiResponse<ReimbursementOutDTO> create(@AuthenticationPrincipal AuthenticatedUser user,
                                                   @RequestBody @Valid ReimbursementCreateInDTO input) {
        return ApiResponse.success(service.create(user.userId(), input));
    }

    @GetMapping("/{id}")
    public ApiResponse<ReimbursementOutDTO> get(@AuthenticationPrincipal AuthenticatedUser user,
                                                @PathVariable Long id) {
        return ApiResponse.success(service.get(user.userId(), id));
    }

    @PutMapping("/{id}")
    public ApiResponse<ReimbursementOutDTO> update(@AuthenticationPrincipal AuthenticatedUser user,
                                                   @PathVariable Long id,
                                                   @RequestBody @Valid ReimbursementUpdateInDTO input) {
        return ApiResponse.success(service.update(user.userId(), id, input));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedUser user,
                                    @PathVariable Long id) {
        service.delete(user.userId(), id);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/receive")
    public ApiResponse<ReimbursementOutDTO> receive(@AuthenticationPrincipal AuthenticatedUser user,
                                                    @PathVariable Long id,
                                                    @RequestBody @Valid ReimbursementReceiveInDTO input) {
        return ApiResponse.success(service.receive(user.userId(), id, input));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<ReimbursementOutDTO> cancel(@AuthenticationPrincipal AuthenticatedUser user,
                                                   @PathVariable Long id) {
        return ApiResponse.success(service.cancel(user.userId(), id));
    }
}
