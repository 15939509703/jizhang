package com.lhj.jizhang.user.controller;

import com.lhj.jizhang.common.api.ApiResponse;
import com.lhj.jizhang.security.model.AuthenticatedUser;
import com.lhj.jizhang.user.dto.AssetSummaryOutDTO;
import com.lhj.jizhang.user.dto.AssetTrendOutDTO;
import com.lhj.jizhang.user.service.AssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "资产总览", description = "资产负债汇总和净资产趋势")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {
    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @Operation(summary = "查询资产汇总")
    @GetMapping("/summary")
    public ApiResponse<AssetSummaryOutDTO> summary(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam Long bookId
    ) {
        return ApiResponse.success(assetService.summary(user.userId(), bookId));
    }

    @Operation(summary = "查询净资产趋势")
    @GetMapping("/trend")
    public ApiResponse<AssetTrendOutDTO> trend(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam Long bookId,
            @RequestParam(required = false) Integer months
    ) {
        return ApiResponse.success(assetService.trend(user.userId(), bookId, months));
    }
}
