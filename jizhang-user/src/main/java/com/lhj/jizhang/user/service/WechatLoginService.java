package com.lhj.jizhang.user.service;

import com.lhj.jizhang.security.model.TokenPair;
import com.lhj.jizhang.security.service.TokenService;
import com.lhj.jizhang.user.client.WechatApiClient;
import com.lhj.jizhang.user.client.WechatPhoneClient;
import com.lhj.jizhang.user.dto.LoginBookOutDTO;
import com.lhj.jizhang.user.dto.LoginUserOutDTO;
import com.lhj.jizhang.user.dto.WechatLoginInDTO;
import com.lhj.jizhang.user.dto.WechatLoginOutDTO;
import com.lhj.jizhang.user.model.LoginUserContext;
import com.lhj.jizhang.user.model.WechatSession;
import org.springframework.stereotype.Service;

@Service
public class WechatLoginService {
    private final WechatApiClient wechatApiClient;
    private final WechatPhoneClient wechatPhoneClient;
    private final WechatUserProvisioningService provisioningService;
    private final WechatSessionStore sessionStore;
    private final TokenService tokenService;

    public WechatLoginService(
            WechatApiClient wechatApiClient,
            WechatPhoneClient wechatPhoneClient,
            WechatUserProvisioningService provisioningService,
            WechatSessionStore sessionStore,
            TokenService tokenService
    ) {
        this.wechatApiClient = wechatApiClient;
        this.wechatPhoneClient = wechatPhoneClient;
        this.provisioningService = provisioningService;
        this.sessionStore = sessionStore;
        this.tokenService = tokenService;
    }

    public WechatLoginOutDTO login(WechatLoginInDTO input) {
        WechatSession session = wechatApiClient.exchangeCode(input.code());
        String phone = input.phoneCode() == null || input.phoneCode().isBlank()
                ? null : wechatPhoneClient.exchangeCode(input.phoneCode());
        LoginUserContext context = provisioningService.findOrCreate(session, input, phone);
        sessionStore.save(context.user().getId(), session.sessionKey());
        TokenPair tokens = tokenService.issue(context.user().getId(), context.auth().getSessionVersion());
        return toOutput(context, tokens);
    }

    private WechatLoginOutDTO toOutput(LoginUserContext context, TokenPair tokens) {
        LoginUserOutDTO user = new LoginUserOutDTO(
                context.user().getId(),
                context.user().getUserNo(),
                context.user().getNickName(),
                UserAvatarService.publicUrl(context.user())
        );
        LoginBookOutDTO book = new LoginBookOutDTO(
                context.defaultBook().getId(),
                context.defaultBook().getBookNo(),
                context.defaultBook().getName()
        );
        return new WechatLoginOutDTO(
                tokens.accessToken(),
                tokens.refreshToken(),
                tokens.expiresIn(),
                user,
                book
        );
    }
}
