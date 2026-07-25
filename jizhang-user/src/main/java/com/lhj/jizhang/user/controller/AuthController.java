package com.lhj.jizhang.user.controller;

import com.lhj.jizhang.common.api.ApiResponse;
import com.lhj.jizhang.security.model.TokenPair;
import com.lhj.jizhang.security.service.TokenService;
import com.lhj.jizhang.user.dto.RefreshTokenInDTO;
import com.lhj.jizhang.user.dto.RefreshTokenOutDTO;
import com.lhj.jizhang.user.dto.PasswordCredentialInDTO;
import com.lhj.jizhang.user.dto.PasswordLoginInDTO;
import com.lhj.jizhang.user.dto.PhoneBindInDTO;
import com.lhj.jizhang.user.dto.PhoneBindingOutDTO;
import com.lhj.jizhang.user.dto.PhoneRegisterInDTO;
import com.lhj.jizhang.user.dto.WechatLoginInDTO;
import com.lhj.jizhang.user.dto.WechatLoginOutDTO;
import com.lhj.jizhang.user.service.PasswordCredentialService;
import com.lhj.jizhang.user.service.PasswordLoginService;
import com.lhj.jizhang.user.service.PhoneBindingService;
import com.lhj.jizhang.user.service.PhoneRegistrationService;
import com.lhj.jizhang.user.service.WechatLoginService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.lhj.jizhang.security.model.AuthenticatedUser;

@Tag(name = "登录认证", description = "微信小程序登录与用户初始化")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final WechatLoginService wechatLoginService;
    private final PasswordLoginService passwordLoginService;
    private final PhoneRegistrationService phoneRegistrationService;
    private final PhoneBindingService phoneBindingService;
    private final PasswordCredentialService passwordCredentialService;
    private final TokenService tokenService;

    public AuthController(
            WechatLoginService wechatLoginService,
            PasswordLoginService passwordLoginService,
            PhoneRegistrationService phoneRegistrationService,
            PhoneBindingService phoneBindingService,
            PasswordCredentialService passwordCredentialService,
            TokenService tokenService
    ) {
        this.wechatLoginService = wechatLoginService;
        this.passwordLoginService = passwordLoginService;
        this.phoneRegistrationService = phoneRegistrationService;
        this.phoneBindingService = phoneBindingService;
        this.passwordCredentialService = passwordCredentialService;
        this.tokenService = tokenService;
    }

    @Operation(summary = "微信小程序登录", description = "使用wx.login临时code换取用户身份并签发JWT")
    @PostMapping("/wechat/login")
    public ApiResponse<WechatLoginOutDTO> wechatLogin(@RequestBody @Valid WechatLoginInDTO input) {
        return ApiResponse.success(wechatLoginService.login(input));
    }

    @Operation(summary = "网页登录")
    @PostMapping("/password/login")
    public ApiResponse<WechatLoginOutDTO> passwordLogin(@RequestBody @Valid PasswordLoginInDTO input) {
        return ApiResponse.success(passwordLoginService.login(input));
    }

    @Operation(summary = "手机号注册", description = "首次注册时优先登录已绑定该手机号的微信账号，否则创建新账号")
    @PostMapping("/phone/register")
    public ApiResponse<WechatLoginOutDTO> phoneRegister(@RequestBody @Valid PhoneRegisterInDTO input) {
        return ApiResponse.success(phoneRegistrationService.register(input));
    }

    @Operation(summary = "微信账号绑定手机号登录")
    @PostMapping("/phone/bind")
    public ApiResponse<Void> bindPhone(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody @Valid PhoneBindInDTO input
    ) {
        phoneBindingService.bind(user.userId(), input);
        return ApiResponse.success(null);
    }

    @Operation(summary = "查询手机号绑定状态")
    @PostMapping("/phone/bind/status")
    public ApiResponse<PhoneBindingOutDTO> phoneBindingStatus(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(phoneBindingService.status(user.userId()));
    }

    @Operation(summary = "绑定或更新网页登录凭证")
    @PostMapping("/password/bind")
    public ApiResponse<Void> bindPassword(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody @Valid PasswordCredentialInDTO input
    ) {
        passwordCredentialService.bind(user.userId(), input);
        return ApiResponse.success(null);
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
