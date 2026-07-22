package com.lhj.jizhang.user.controller;

import com.lhj.jizhang.common.api.ApiResponse;
import com.lhj.jizhang.security.model.AuthenticatedUser;
import com.lhj.jizhang.user.dto.StatisticsDashboardOutDTO;
import com.lhj.jizhang.user.service.StatisticsService;
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

@Tag(name = "统计分析", description = "今日、月度、趋势、分类、账户和年度统计")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/statistics")
public class StatisticsController {
    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @Operation(summary = "查询统计分析看板")
    @GetMapping
    public ApiResponse<StatisticsDashboardOutDTO> dashboard(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam Long bookId,
            @DateTimeFormat(pattern = "yyyy-MM") @RequestParam(required = false) YearMonth month,
            @RequestParam(required = false) Integer year
    ) {
        return ApiResponse.success(statisticsService.dashboard(user.userId(), bookId, month, year));
    }
}
