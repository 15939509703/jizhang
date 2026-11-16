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

    /**
     * 初始化手机号注册服务。
     *
     * @param userAuthMapper 数据访问器
     * @param credentialService 关联服务
     * @param passwordLoginService 关联服务
     * @param provisioningService 关联服务
     */
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

    /**
     * 注册手机号账户或关联已有用户。
     *
     * @param input 请求参数
     * @return 登录结果
     */
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

    /**
     * 查询手机号认证信息。
     *
     * @param phone 手机号
     * @return 用户认证关系实体
     */
    private UserAuthEntity findPhoneAuth(String phone) {
        return userAuthMapper.selectOne(Wrappers.<UserAuthEntity>lambdaQuery()
                .eq(UserAuthEntity::getProvider, PHONE_PROVIDER)
                .eq(UserAuthEntity::getOpenid, phone));
    }

    /**
     * 判断用户是否已有微信认证关系。
     *
     * @param userId 用户ID
     * @return 条件是否成立
     */
    private boolean hasWechatAuth(Long userId) {
        return userAuthMapper.selectCount(Wrappers.<UserAuthEntity>lambdaQuery()
                .eq(UserAuthEntity::getUserId, userId)
                .eq(UserAuthEntity::getProvider, WECHAT_PROVIDER)
                .eq(UserAuthEntity::getStatus, 1)) > 0;
    }

    /**
     * 判断手机号认证关系是否有效。
     *
     * @param auth 认证关系实体
     * @return 条件是否成立
     */
    private boolean isActive(UserAuthEntity auth) {
        return auth.getStatus() != null && auth.getStatus() == 1;
    }

    /**
     * 构建手机号注册业务异常。
     *
     * @return 对应业务异常
     */
    private BusinessException phoneAlreadyRegistered() {
        return new BusinessException(
                ErrorCodes.PHONE_ALREADY_REGISTERED,
                "手机号已注册，请直接登录",
                HttpStatus.CONFLICT
        );
    }

    /**
     * 构建手机号注册业务异常。
     *
     * @return 对应业务异常
     */
    private BusinessException invalidBinding() {
        return new BusinessException(
                ErrorCodes.PHONE_BINDING_INVALID,
                "请先在微信小程序中设置登录密码，再用该手机号登录",
                HttpStatus.CONFLICT
        );
    }
}
