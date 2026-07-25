package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.user.dto.PasswordCredentialInDTO;
import com.lhj.jizhang.user.dto.PhoneBindInDTO;
import com.lhj.jizhang.user.dto.PhoneBindingOutDTO;
import com.lhj.jizhang.user.entity.UserAuthEntity;
import com.lhj.jizhang.user.entity.UserCredentialEntity;
import com.lhj.jizhang.user.mapper.UserAuthMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PhoneBindingService {
    private static final String PHONE_PROVIDER = "PHONE";
    private static final String WECHAT_PROVIDER = "WECHAT";

    private final UserAuthMapper userAuthMapper;
    private final PasswordCredentialService credentialService;

    public PhoneBindingService(UserAuthMapper userAuthMapper, PasswordCredentialService credentialService) {
        this.userAuthMapper = userAuthMapper;
        this.credentialService = credentialService;
    }

    @Transactional
    public void bind(Long userId, PhoneBindInDTO input) {
        requireWechatAuth(userId);
        String phone = input.phone().trim();
        ensurePhoneAvailable(userId, phone);
        try {
            UserAuthEntity phoneAuth = savePhoneAuth(userId, phone);
            deactivateOtherPhoneAuths(userId, phoneAuth.getId());
            credentialService.bind(userId, new PasswordCredentialInDTO(phone, input.password()));
        } catch (DuplicateKeyException exception) {
            throw phoneAlreadyRegistered();
        }
    }

    public PhoneBindingOutDTO status(Long userId) {
        UserAuthEntity phoneAuth = findCurrentPhoneAuth(userId);
        if (phoneAuth == null || phoneAuth.getStatus() == null || phoneAuth.getStatus() != 1) {
            return new PhoneBindingOutDTO(false, null);
        }
        UserCredentialEntity credential = credentialService.findByUsername(phoneAuth.getOpenid());
        boolean bound = credential != null && userId.equals(credential.getUserId());
        return new PhoneBindingOutDTO(bound, phoneAuth.getOpenid());
    }

    private void requireWechatAuth(Long userId) {
        long count = userAuthMapper.selectCount(Wrappers.<UserAuthEntity>lambdaQuery()
                .eq(UserAuthEntity::getUserId, userId)
                .eq(UserAuthEntity::getProvider, WECHAT_PROVIDER)
                .eq(UserAuthEntity::getStatus, 1));
        if (count == 0) {
            throw new BusinessException(
                    ErrorCodes.PHONE_BINDING_INVALID,
                    "当前账号不是微信小程序账号，不能绑定手机号",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    private void ensurePhoneAvailable(Long userId, String phone) {
        UserAuthEntity phoneAuth = findPhoneAuth(phone);
        if (phoneAuth != null && !phoneAuth.getUserId().equals(userId)) {
            throw phoneAlreadyRegistered();
        }
        UserCredentialEntity credential = credentialService.findAnyByUsername(phone);
        if (credential != null && !credential.getUserId().equals(userId)) {
            throw phoneAlreadyRegistered();
        }
    }

    private UserAuthEntity savePhoneAuth(Long userId, String phone) {
        UserAuthEntity phoneAuth = findPhoneAuth(phone);
        if (phoneAuth == null) {
            phoneAuth = findCurrentPhoneAuth(userId);
        }
        if (phoneAuth == null) {
            phoneAuth = newPhoneAuth(userId, phone);
            userAuthMapper.insert(phoneAuth);
            return phoneAuth;
        }
        phoneAuth.setOpenid(phone);
        phoneAuth.setSessionVersion(phoneAuth.getSessionVersion() == null ? 1 : phoneAuth.getSessionVersion());
        phoneAuth.setStatus(1);
        phoneAuth.setModifier(String.valueOf(userId));
        userAuthMapper.updateById(phoneAuth);
        return phoneAuth;
    }

    private UserAuthEntity findPhoneAuth(String phone) {
        return userAuthMapper.selectOne(Wrappers.<UserAuthEntity>lambdaQuery()
                .eq(UserAuthEntity::getProvider, PHONE_PROVIDER)
                .eq(UserAuthEntity::getOpenid, phone));
    }

    private UserAuthEntity findCurrentPhoneAuth(Long userId) {
        return userAuthMapper.selectOne(Wrappers.<UserAuthEntity>lambdaQuery()
                .eq(UserAuthEntity::getUserId, userId)
                .eq(UserAuthEntity::getProvider, PHONE_PROVIDER)
                .orderByDesc(UserAuthEntity::getStatus)
                .orderByAsc(UserAuthEntity::getId)
                .last("LIMIT 1"));
    }

    private UserAuthEntity newPhoneAuth(Long userId, String phone) {
        UserAuthEntity phoneAuth = new UserAuthEntity();
        phoneAuth.setUserId(userId);
        phoneAuth.setProvider(PHONE_PROVIDER);
        phoneAuth.setOpenid(phone);
        phoneAuth.setSessionVersion(1);
        phoneAuth.setStatus(1);
        phoneAuth.setCreator(String.valueOf(userId));
        phoneAuth.setModifier(String.valueOf(userId));
        return phoneAuth;
    }

    private void deactivateOtherPhoneAuths(Long userId, Long activeAuthId) {
        if (activeAuthId == null) {
            return;
        }
        List<UserAuthEntity> otherAuths = userAuthMapper.selectList(Wrappers.<UserAuthEntity>lambdaQuery()
                .eq(UserAuthEntity::getUserId, userId)
                .eq(UserAuthEntity::getProvider, PHONE_PROVIDER)
                .ne(UserAuthEntity::getId, activeAuthId)
                .eq(UserAuthEntity::getStatus, 1));
        for (UserAuthEntity auth : otherAuths) {
            auth.setStatus(0);
            auth.setModifier(String.valueOf(userId));
            userAuthMapper.updateById(auth);
        }
    }

    private BusinessException phoneAlreadyRegistered() {
        return new BusinessException(
                ErrorCodes.PHONE_ALREADY_REGISTERED,
                "手机号已被其他账号使用",
                HttpStatus.CONFLICT
        );
    }
}
