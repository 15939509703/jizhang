package com.lhj.jizhang.user.controller;

import com.lhj.jizhang.common.api.ApiResponse;
import com.lhj.jizhang.security.model.AuthenticatedUser;
import com.lhj.jizhang.user.dto.SavingsContributionInDTO;
import com.lhj.jizhang.user.dto.SavingsGoalCreateInDTO;
import com.lhj.jizhang.user.dto.SavingsGoalOutDTO;
import com.lhj.jizhang.user.dto.SavingsGoalUpdateInDTO;
import com.lhj.jizhang.user.service.SavingsGoalService;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/v1/savings-goals")
public class SavingsGoalController {
    private final SavingsGoalService service;
    public SavingsGoalController(SavingsGoalService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<SavingsGoalOutDTO>> list(@AuthenticationPrincipal AuthenticatedUser user,
                                                     @RequestParam Long bookId,
                                                     @RequestParam(required = false) String status) {
        return ApiResponse.success(service.list(user.userId(), bookId, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<SavingsGoalOutDTO> get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return ApiResponse.success(service.get(user.userId(), id));
    }

    @PostMapping
    public ApiResponse<SavingsGoalOutDTO> create(@AuthenticationPrincipal AuthenticatedUser user,
                                                 @RequestBody @Valid SavingsGoalCreateInDTO input) {
        return ApiResponse.success(service.create(user.userId(), input));
    }

    @Operation(summary = "修改储蓄目标")
    @PutMapping("/{id}")
    public ApiResponse<SavingsGoalOutDTO> update(@AuthenticationPrincipal AuthenticatedUser user,
                                                 @PathVariable Long id,
                                                 @RequestBody @Valid SavingsGoalUpdateInDTO input) {
        return ApiResponse.success(service.update(user.userId(), id, input));
    }

    @Operation(summary = "删除储蓄目标")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        service.delete(user.userId(), id);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/contributions")
    public ApiResponse<SavingsGoalOutDTO> contribute(@AuthenticationPrincipal AuthenticatedUser user,
                                                     @PathVariable Long id,
                                                     @RequestBody @Valid SavingsContributionInDTO input) {
        return ApiResponse.success(service.addContribution(user.userId(), id, input));
    }

    @PostMapping("/{id}/status/{status}")
    public ApiResponse<SavingsGoalOutDTO> status(@AuthenticationPrincipal AuthenticatedUser user,
                                                 @PathVariable Long id, @PathVariable String status) {
        return ApiResponse.success(service.changeStatus(user.userId(), id, status.toUpperCase()));
    }

    @DeleteMapping("/{id}/contributions/{contributionId}")
    public ApiResponse<SavingsGoalOutDTO> removeContribution(@AuthenticationPrincipal AuthenticatedUser user,
                                                             @PathVariable Long id,
                                                             @PathVariable Long contributionId) {
        return ApiResponse.success(service.removeManualContribution(user.userId(), id, contributionId));
    }
}
