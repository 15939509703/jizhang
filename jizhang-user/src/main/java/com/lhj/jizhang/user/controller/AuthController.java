package com.lhj.jizhang.user.controller;

import com.lhj.jizhang.common.api.ApiResponse;
import com.lhj.jizhang.security.model.TokenPair;
import com.lhj.jizhang.security.service.TokenService;
import com.lhj.jizhang.user.dto.RefreshTokenInDTO;
import com.lhj.jizhang.user.dto.RefreshTokenOutDTO;
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
    private final TokenService tokenService;

    public AuthController(WechatLoginService wechatLoginService, TokenService tokenService) {
        this.wechatLoginService = wechatLoginService;
        this.tokenService = tokenService;
    }

    @Operation(summary = "微信小程序登录", description = "使用wx.login临时code换取用户身份并签发JWT")
    @PostMapping("/wechat/login")
    public ApiResponse<WechatLoginOutDTO> wechatLogin(@RequestBody @Valid WechatLoginInDTO input) {
        return ApiResponse.success(wechatLoginService.login(input));
    }

    @Operation(summary = "刷新访问令牌", description = "使用一次性刷新令牌轮换新的访问令牌和刷新令牌")
    @PostMapping("/refresh")
    public ApiResponse<RefreshTokenOutDTO> refresh(@RequestBody @Valid RefreshTokenInDTO input) {
        TokenPair tokens = tokenService.refresh(input.refreshToken());
        return ApiResponse.success(new RefreshTokenOutDTO(
                tokens.accessToken(),
                tokens.refreshToken(),
                tokens.expiresIn()
        ));
    }
}
