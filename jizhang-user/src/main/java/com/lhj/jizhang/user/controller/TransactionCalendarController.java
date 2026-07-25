package com.lhj.jizhang.user.controller;

import com.lhj.jizhang.common.api.ApiResponse;
import com.lhj.jizhang.security.model.AuthenticatedUser;
import com.lhj.jizhang.user.dto.TransactionCalendarOutDTO;
import com.lhj.jizhang.user.service.TransactionCalendarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

@Tag(name = "账单日历", description = "按账本自然月查询每日收支")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/transaction-calendar")
public class TransactionCalendarController {
    private final TransactionCalendarService calendarService;

    public TransactionCalendarController(TransactionCalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @Operation(summary = "查询月度账单日历")
    @GetMapping
    public ApiResponse<TransactionCalendarOutDTO> get(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam Long bookId,
            @DateTimeFormat(pattern = "yyyy-MM") @RequestParam(required = false) YearMonth month
    ) {
        return ApiResponse.success(calendarService.get(user.userId(), bookId, month));
    }
}
