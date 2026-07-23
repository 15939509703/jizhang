package com.lhj.jizhang.user.controller;

import com.lhj.jizhang.common.api.ApiResponse;
import com.lhj.jizhang.security.model.AuthenticatedUser;
import com.lhj.jizhang.user.dto.LoanCreateInDTO;
import com.lhj.jizhang.user.dto.LoanOutDTO;
import com.lhj.jizhang.user.dto.LoanPageOutDTO;
import com.lhj.jizhang.user.dto.LoanReminderInDTO;
import com.lhj.jizhang.user.dto.LoanRepaymentInDTO;
import com.lhj.jizhang.user.dto.LoanSummaryOutDTO;
import com.lhj.jizhang.user.dto.LoanUpdateInDTO;
import com.lhj.jizhang.user.service.LoanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "借还管理", description = "借入、借出、还款和催收记录")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/loans")
public class LoanController {
    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @Operation(summary = "新增借还记录")
    @PostMapping
    public ApiResponse<LoanOutDTO> create(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody @Valid LoanCreateInDTO input
    ) {
        return ApiResponse.success(loanService.create(user.userId(), input));
    }

    @Operation(summary = "分页查询借还记录")
    @GetMapping
    public ApiResponse<LoanPageOutDTO> list(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "账本ID", required = true, example = "1") @RequestParam Long bookId,
            @Parameter(description = "借还类型", example = "LEND",
                    schema = @Schema(allowableValues = {"BORROW", "LEND"}))
            @RequestParam(required = false) String loanType,
            @Parameter(description = "状态", example = "OPEN",
                    schema = @Schema(allowableValues = {"OPEN", "PARTIAL", "CLOSED", "OVERDUE"}))
            @RequestParam(required = false) String status,
            @Parameter(description = "上一页最后一条记录ID", example = "100")
            @RequestParam(required = false) Long cursorId,
            @Parameter(description = "每页条数，默认20，最大100", example = "20")
            @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.success(loanService.list(user.userId(), bookId, loanType, status, cursorId, limit));
    }

    @Operation(summary = "查询借还汇总")
    @GetMapping("/summary")
    public ApiResponse<LoanSummaryOutDTO> summary(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "账本ID", required = true, example = "1") @RequestParam Long bookId
    ) {
        return ApiResponse.success(loanService.summary(user.userId(), bookId));
    }

    @Operation(summary = "查询借还详情")
    @GetMapping("/{id}")
    public ApiResponse<LoanOutDTO> get(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id
    ) {
        return ApiResponse.success(loanService.get(user.userId(), id));
    }

    @Operation(summary = "修改借还记录")
    @PutMapping("/{id}")
    public ApiResponse<LoanOutDTO> update(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @RequestBody @Valid LoanUpdateInDTO input
    ) {
        return ApiResponse.success(loanService.update(user.userId(), id, input));
    }

    @Operation(summary = "登记还款或收款")
    @PostMapping("/{id}/repayments")
    public ApiResponse<LoanOutDTO> repay(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @RequestBody @Valid LoanRepaymentInDTO input
    ) {
        return ApiResponse.success(loanService.repay(user.userId(), id, input));
    }

    @Operation(summary = "登记催收记录")
    @PostMapping("/{id}/reminders")
    public ApiResponse<LoanOutDTO> remind(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @RequestBody @Valid LoanReminderInDTO input
    ) {
        return ApiResponse.success(loanService.remind(user.userId(), id, input));
    }
}
