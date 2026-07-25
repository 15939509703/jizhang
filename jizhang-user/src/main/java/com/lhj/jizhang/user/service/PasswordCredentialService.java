package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.user.dto.PasswordCredentialInDTO;
import com.lhj.jizhang.user.entity.UserCredentialEntity;
import com.lhj.jizhang.user.entity.UserEntity;
import com.lhj.jizhang.user.mapper.UserCredentialMapper;
import com.lhj.jizhang.user.mapper.UserMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PasswordCredentialService {
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_MINUTES = 15;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final UserCredentialMapper credentialMapper;
    private final UserMapper userMapper;

    public PasswordCredentialService(UserCredentialMapper credentialMapper, UserMapper userMapper) {
        this.credentialMapper = credentialMapper;
        this.userMapper = userMapper;
    }

    @Transactional
    public void bind(Long userId, PasswordCredentialInDTO input) {
        requireActiveUser(userId);
        String username = normalize(input.username());
        UserCredentialEntity sameUsername = findAnyByUsername(username);
        if (sameUsername != null && !sameUsername.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCodes.PASSWORD_CREDENTIAL_EXISTS, "登录账号已被使用");
        }
        UserCredentialEntity credential = findAnyByUserId(userId);
        if (credential == null) {
            credential = newCredential(userId, username, input.password());
            credentialMapper.insert(credential);
            return;
        }
        updateCredential(credential, username, input.password(), userId);
        credentialMapper.updateById(credential);
    }

    public UserCredentialEntity findByUsername(String username) {
        return credentialMapper.selectOne(Wrappers.<UserCredentialEntity>lambdaQuery()
                .eq(UserCredentialEntity::getUsername, normalize(username))
                .eq(UserCredentialEntity::getStatus, 1));
    }

    public UserCredentialEntity findAnyByUsername(String username) {
        return credentialMapper.selectOne(Wrappers.<UserCredentialEntity>lambdaQuery()
                .eq(UserCredentialEntity::getUsername, normalize(username)));
    }

    public UserCredentialEntity findByUserId(Long userId) {
        return credentialMapper.selectOne(Wrappers.<UserCredentialEntity>lambdaQuery()
                .eq(UserCredentialEntity::getUserId, userId)
                .eq(UserCredentialEntity::getStatus, 1));
    }

    private UserCredentialEntity findAnyByUserId(Long userId) {
        return credentialMapper.selectOne(Wrappers.<UserCredentialEntity>lambdaQuery()
                .eq(UserCredentialEntity::getUserId, userId));
    }

    public boolean matches(String password, String passwordHash) {
        return passwordEncoder.matches(password, passwordHash);
    }

    @Transactional
    public void recordFailure(UserCredentialEntity credential) {
        int failedCount = (credential.getFailedCount() == null ? 0 : credential.getFailedCount()) + 1;
        credential.setFailedCount(failedCount);
        if (failedCount >= MAX_FAILED_ATTEMPTS) {
            credential.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
            credential.setFailedCount(0);
        }
        credentialMapper.updateById(credential);
    }

    @Transactional
    public void recordSuccess(UserCredentialEntity credential) {
        credential.setFailedCount(0);
        credential.setLockedUntil(null);
        credential.setLastLoginTime(LocalDateTime.now());
        credentialMapper.updateById(credential);
    }

    public boolean isLocked(UserCredentialEntity credential) {
        return credential.getLockedUntil() != null && credential.getLockedUntil().isAfter(LocalDateTime.now());
    }

    private UserCredentialEntity newCredential(Long userId, String username, String password) {
        UserCredentialEntity credential = new UserCredentialEntity();
        credential.setUserId(userId);
        credential.setUsername(username);
        credential.setPasswordHash(passwordEncoder.encode(password));
        credential.setFailedCount(0);
        credential.setStatus(1);
        credential.setCreator(String.valueOf(userId));
        credential.setModifier(String.valueOf(userId));
        return credential;
    }

    private void updateCredential(UserCredentialEntity credential, String username, String password, Long userId) {
        credential.setUsername(username);
        credential.setPasswordHash(passwordEncoder.encode(password));
        credential.setFailedCount(0);
        credential.setLockedUntil(null);
        credential.setStatus(1);
        credential.setModifier(String.valueOf(userId));
    }

    private UserEntity requireActiveUser(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCodes.USER_DATA_INVALID, "用户状态异常，请重新登录");
        }
        return user;
    }

    private String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }
}
