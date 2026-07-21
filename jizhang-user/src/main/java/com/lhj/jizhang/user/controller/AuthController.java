package com.lhj.jizhang.user.controller;

import com.lhj.jizhang.common.api.ApiResponse;
import com.lhj.jizhang.user.dto.WechatLoginInDTO;
import com.lhj.jizhang.user.dto.WechatLoginOutDTO;
import com.lhj.jizhang.user.service.WechatLoginService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "登录认证", description = "微信小程序登录与用户初始化")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final WechatLoginService wechatLoginService;

    public AuthController(WechatLoginService wechatLoginService) {
        this.wechatLoginService = wechatLoginService;
    }

    @Operation(summary = "微信小程序登录", description = "使用wx.login临时code换取用户身份并签发JWT")
    @PostMapping("/wechat/login")
    public ApiResponse<WechatLoginOutDTO> wechatLogin(@RequestBody @Valid WechatLoginInDTO input) {
        return ApiResponse.success(wechatLoginService.login(input));
    }
}
