package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.user.dto.PasswordLoginInDTO;
import com.lhj.jizhang.user.dto.PhoneRegisterInDTO;
import com.lhj.jizhang.user.dto.WechatLoginOutDTO;
import com.lhj.jizhang.user.entity.UserAuthEntity;
import com.lhj.jizhang.user.entity.UserCredentialEntity;
import com.lhj.jizhang.user.mapper.UserAuthMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PhoneRegistrationService {
    private static final String PHONE_PROVIDER = "PHONE";
    private static final String WECHAT_PROVIDER = "WECHAT";

    private final UserAuthMapper userAuthMapper;
    private final PasswordCredentialService credentialService;
    private final PasswordLoginService passwordLoginService;
    private final PhoneAccountProvisioningService provisioningService;

    public PhoneRegistrationService(
            UserAuthMapper userAuthMapper,
            PasswordCredentialService credentialService,
            PasswordLoginService passwordLoginService,
            PhoneAccountProvisioningService provisioningService
    ) {
        this.userAuthMapper = userAuthMapper;
        this.credentialService = credentialService;
        this.passwordLoginService = passwordLoginService;
        this.provisioningService = provisioningService;
    }

    public WechatLoginOutDTO register(PhoneRegisterInDTO input) {
        String phone = input.phone().trim();
        UserAuthEntity phoneAuth = findPhoneAuth(phone);
        UserCredentialEntity credential = credentialService.findByUsername(phone);
        if (phoneAuth == null) {
            if (credential != null) {
                throw phoneAlreadyRegistered();
            }
            return provisioningService.create(phone, input.password());
        }
        if (!isActive(phoneAuth) || !hasWechatAuth(phoneAuth.getUserId())) {
            throw phoneAlreadyRegistered();
        }
        if (credential == null || !phoneAuth.getUserId().equals(credential.getUserId())) {
            throw invalidBinding();
        }
        return passwordLoginService.login(new PasswordLoginInDTO(phone, input.password()));
    }

    private UserAuthEntity findPhoneAuth(String phone) {
        return userAuthMapper.selectOne(Wrappers.<UserAuthEntity>lambdaQuery()
                .eq(UserAuthEntity::getProvider, PHONE_PROVIDER)
                .eq(UserAuthEntity::getOpenid, phone));
    }

    private boolean hasWechatAuth(Long userId) {
        return userAuthMapper.selectCount(Wrappers.<UserAuthEntity>lambdaQuery()
                .eq(UserAuthEntity::getUserId, userId)
                .eq(UserAuthEntity::getProvider, WECHAT_PROVIDER)
                .eq(UserAuthEntity::getStatus, 1)) > 0;
    }

    private boolean isActive(UserAuthEntity auth) {
        return auth.getStatus() != null && auth.getStatus() == 1;
    }

    private BusinessException phoneAlreadyRegistered() {
        return new BusinessException(
                ErrorCodes.PHONE_ALREADY_REGISTERED,
                "手机号已注册，请直接登录",
                HttpStatus.CONFLICT
        );
    }

    private BusinessException invalidBinding() {
        return new BusinessException(
                ErrorCodes.PHONE_BINDING_INVALID,
                "手机号绑定数据异常，请在微信小程序中重新绑定",
                HttpStatus.CONFLICT
        );
    }
}
