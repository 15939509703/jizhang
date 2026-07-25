package com.lhj.jizhang.user.controller;

import com.lhj.jizhang.common.api.ApiResponse;
import com.lhj.jizhang.user.config.FeatureProperties;
import com.lhj.jizhang.user.dto.FeatureFlagsOutDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "功能开关", description = "查询当前环境开放的客户端功能")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/features")
public class FeatureController {
    private final FeatureProperties properties;

    public FeatureController(FeatureProperties properties) {
        this.properties = properties;
    }

    @Operation(summary = "查询功能开关")
    @GetMapping
    public ApiResponse<FeatureFlagsOutDTO> get() {
        return ApiResponse.success(new FeatureFlagsOutDTO(properties.recurringTransactions(),
                properties.transactionCalendar(), properties.budgetForecast(), properties.assetDashboard(),
                properties.reimbursement(), properties.savingsGoals(), properties.consumptionReports(),
                properties.dataSecurity(), properties.familySharing(), properties.wechatSubscriptions()));
    }
}
