package com.lhj.jizhang.user.controller;

import com.lhj.jizhang.common.api.ApiResponse;
import com.lhj.jizhang.security.model.AuthenticatedUser;
import com.lhj.jizhang.user.dto.TransactionCreateInDTO;
import com.lhj.jizhang.user.dto.TransactionOutDTO;
import com.lhj.jizhang.user.dto.TransactionPageOutDTO;
import com.lhj.jizhang.user.dto.TransactionSummaryOutDTO;
import com.lhj.jizhang.user.service.TransactionService;
import com.lhj.jizhang.user.service.TransactionSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.YearMonth;

@Tag(name = "账单管理", description = "收支、转账账单的新增、查询与作废")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {
    private final TransactionService transactionService;
    private final TransactionSummaryService transactionSummaryService;

    public TransactionController(
            TransactionService transactionService,
            TransactionSummaryService transactionSummaryService
    ) {
        this.transactionService = transactionService;
        this.transactionSummaryService = transactionSummaryService;
    }

    @Operation(summary = "新增账单", description = "新增支出、收入或转账账单，并同步更新账户余额")
    @PostMapping
    public ApiResponse<TransactionOutDTO> create(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody @Valid TransactionCreateInDTO input
    ) {
        return ApiResponse.success(transactionService.create(user.userId(), input));
    }

    @Operation(summary = "分页查询账单", description = "按账本和筛选条件进行游标分页查询")
    @GetMapping
    public ApiResponse<TransactionPageOutDTO> list(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "账本ID", required = true, example = "1") @RequestParam Long bookId,
            @Parameter(description = "账单类型", example = "EXPENSE",
                    schema = @Schema(allowableValues = {"EXPENSE", "INCOME", "TRANSFER"}))
            @RequestParam(required = false) String type,
            @Parameter(description = "账单状态", example = "EFFECTIVE",
                    schema = @Schema(allowableValues = {"EFFECTIVE", "VOIDED", "REVERSED"}))
            @RequestParam(required = false) String status,
            @Parameter(description = "分类ID", example = "1") @RequestParam(required = false) Long categoryId,
            @Parameter(description = "发生时间起点（ISO-8601）", example = "2026-07-01T00:00:00Z")
            @RequestParam(required = false) Instant startAt,
            @Parameter(description = "发生时间终点（ISO-8601）", example = "2026-08-01T00:00:00Z")
            @RequestParam(required = false) Instant endAt,
            @Parameter(description = "上一页最后一条账单ID", example = "100")
            @RequestParam(required = false) Long cursorId,
            @Parameter(description = "每页条数，默认20，最大100", example = "20")
            @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.success(transactionService.list(user.userId(), bookId, type, status, categoryId,
                startAt, endAt, cursorId, limit));
    }

    @Operation(summary = "查询月度收支汇总", description = "按账本时区统计指定月份的有效收入、支出和结余")
    @GetMapping("/summary")
    public ApiResponse<TransactionSummaryOutDTO> summary(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "账本ID", required = true, example = "1") @RequestParam Long bookId,
            @Parameter(description = "统计月份，默认当前月", example = "2026-07")
            @DateTimeFormat(pattern = "yyyy-MM") @RequestParam(required = false) YearMonth month
    ) {
        return ApiResponse.success(transactionSummaryService.summarize(user.userId(), bookId, month));
    }

    @Operation(summary = "作废账单", description = "作废有效账单并生成账户余额冲正记录")
    @PostMapping("/{id}/void")
    public ApiResponse<TransactionOutDTO> voidTransaction(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "账单ID", required = true, example = "1") @PathVariable Long id
    ) {
        return ApiResponse.success(transactionService.voidTransaction(user.userId(), id));
    }
}
