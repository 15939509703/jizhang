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

@Service
public class PhoneBindingService {
    private static final String PHONE_PROVIDER = "PHONE";

    private final UserAuthMapper userAuthMapper;
    private final PasswordCredentialService credentialService;

    public PhoneBindingService(UserAuthMapper userAuthMapper, PasswordCredentialService credentialService) {
        this.userAuthMapper = userAuthMapper;
        this.credentialService = credentialService;
    }

    /** 为已由微信核验并归属当前用户的手机号设置或修改登录密码。 */
    @Transactional
    public void bind(Long userId, PhoneBindInDTO input) {
        String phone = input.phone().trim();
        UserAuthEntity current = findCurrentPhoneAuth(userId);
        if (current == null || current.getStatus() == null || current.getStatus() != 1
                || !phone.equals(current.getOpenid())) {
            throw new BusinessException(ErrorCodes.PHONE_BINDING_INVALID,
                    "请先授权当前微信手机号，已绑定的手机号不能修改", HttpStatus.CONFLICT);
        }
        ensurePhoneAvailable(userId, phone);
        credentialService.bind(userId, new PasswordCredentialInDTO(phone, input.password()));
    }

    /** 将微信核验的手机号关联到当前用户，已有手机号不得更换。 */
    @Transactional
    public void claimVerifiedPhone(Long userId, String phone) {
        UserAuthEntity current = findCurrentPhoneAuth(userId);
        if (current != null && !phone.equals(current.getOpenid())) {
            throw new BusinessException(ErrorCodes.PHONE_BINDING_INVALID,
                    "当前账号已绑定其他手机号，不能修改", HttpStatus.CONFLICT);
        }
        ensurePhoneAvailable(userId, phone);
        if (current == null) {
            try {
                userAuthMapper.insert(newPhoneAuth(userId, phone));
            } catch (DuplicateKeyException exception) {
                throw phoneAlreadyRegistered();
            }
        } else if (current.getStatus() == null || current.getStatus() != 1) {
            current.setStatus(1);
            current.setModifier(String.valueOf(userId));
            userAuthMapper.updateById(current);
        }
    }

    /** 查询微信手机号是否已经对应一个 App 或小程序用户。 */
    public Long findPhoneOwner(String phone) {
        UserAuthEntity auth = findPhoneAuth(phone);
        return auth == null ? null : auth.getUserId();
    }

    /** 禁止旧通用密码接口更改已绑定账号的手机号。 */
    public void assertUsernameUnchanged(Long userId, String username) {
        UserAuthEntity current = findCurrentPhoneAuth(userId);
        String normalized = username.trim();
        if (current == null && normalized.matches("^1[3-9]\\d{9}$")) {
            throw new BusinessException(ErrorCodes.PHONE_BINDING_INVALID,
                    "请先授权当前微信手机号", HttpStatus.CONFLICT);
        }
        if (current != null && !current.getOpenid().equals(normalized)) {
            throw new BusinessException(ErrorCodes.PHONE_BINDING_INVALID,
                    "手机号不能修改", HttpStatus.CONFLICT);
        }
    }

    /** 返回手机号及其密码是否已设置。 */
    public PhoneBindingOutDTO status(Long userId) {
        UserAuthEntity phoneAuth = findCurrentPhoneAuth(userId);
        if (phoneAuth == null || phoneAuth.getStatus() == null || phoneAuth.getStatus() != 1) {
            return new PhoneBindingOutDTO(false, null);
        }
        UserCredentialEntity credential = credentialService.findByUsername(phoneAuth.getOpenid());
        boolean bound = credential != null && userId.equals(credential.getUserId());
        return new PhoneBindingOutDTO(bound, phoneAuth.getOpenid());
    }

    /** 保证手机号和密码凭证均未被其他用户占用。 */
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

    private BusinessException phoneAlreadyRegistered() {
        return new BusinessException(
                ErrorCodes.PHONE_ALREADY_REGISTERED,
                "手机号已关联其他账号，无法自动合并账本，请联系管理员处理",
                HttpStatus.CONFLICT
        );
    }
}
