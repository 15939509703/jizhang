package com.lhj.jizhang.user.controller;

import com.lhj.jizhang.common.api.ApiResponse;
import com.lhj.jizhang.security.model.AuthenticatedUser;
import com.lhj.jizhang.user.dto.WechatSubscriptionInDTO;
import com.lhj.jizhang.user.dto.WechatSubscriptionOutDTO;
import com.lhj.jizhang.user.service.WechatSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "微信订阅通知", description = "通知场景的订阅设置")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/wechat-subscriptions")
public class WechatSubscriptionController {
    private final WechatSubscriptionService service;
    public WechatSubscriptionController(WechatSubscriptionService service) { this.service = service; }
    @Operation(summary = "查询订阅通知设置")
    @GetMapping public ApiResponse<List<WechatSubscriptionOutDTO>> settings(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(service.settings(user.userId()));
    }
    @Operation(summary = "修改订阅通知设置")
    @PutMapping public ApiResponse<WechatSubscriptionOutDTO> update(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
                                                                    @RequestBody @Valid WechatSubscriptionInDTO input) {
        return ApiResponse.success(service.update(user.userId(), input));
    }
}
