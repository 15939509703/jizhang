package com.lhj.jizhang.user.controller;

import com.lhj.jizhang.common.api.ApiResponse;
import com.lhj.jizhang.security.model.AuthenticatedUser;
import com.lhj.jizhang.user.dto.WechatSubscriptionInDTO;
import com.lhj.jizhang.user.dto.WechatSubscriptionOutDTO;
import com.lhj.jizhang.user.service.WechatSubscriptionService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/wechat-subscriptions")
public class WechatSubscriptionController {
    private final WechatSubscriptionService service;
    public WechatSubscriptionController(WechatSubscriptionService service) { this.service = service; }
    @GetMapping public ApiResponse<List<WechatSubscriptionOutDTO>> settings(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(service.settings(user.userId()));
    }
    @PutMapping public ApiResponse<WechatSubscriptionOutDTO> update(@AuthenticationPrincipal AuthenticatedUser user,
                                                                    @RequestBody @Valid WechatSubscriptionInDTO input) {
        return ApiResponse.success(service.update(user.userId(), input));
    }
}
