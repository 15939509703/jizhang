package com.lhj.jizhang.user.controller;

import com.lhj.jizhang.common.api.ApiResponse;
import com.lhj.jizhang.security.model.AuthenticatedUser;
import com.lhj.jizhang.user.dto.ConsumptionReportOutDTO;
import com.lhj.jizhang.user.service.ConsumptionReportService;
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

@Tag(name = "消费报告", description = "月度消费汇总和分类环比")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/consumption-reports")
public class ConsumptionReportController {
    private final ConsumptionReportService service;
    public ConsumptionReportController(ConsumptionReportService service) { this.service = service; }

    @Operation(summary = "查询月度消费报告")
    @GetMapping
    public ApiResponse<ConsumptionReportOutDTO> get(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
                                                    @RequestParam Long bookId,
                                                    @RequestParam(required = false)
                                                    @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return ApiResponse.success(service.get(user.userId(), bookId, month));
    }
}
