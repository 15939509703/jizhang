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

    /**
     * 初始化密码凭证服务。
     *
     * @param credentialMapper 数据访问器
     * @param userMapper 数据访问器
     */
    public PasswordCredentialService(UserCredentialMapper credentialMapper, UserMapper userMapper) {
        this.credentialMapper = credentialMapper;
        this.userMapper = userMapper;
    }

    /**
     * 绑定密码凭证。
     *
     * @param userId 用户ID
     * @param input 请求参数
     */
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

    /**
     * 按用户名查找有效密码凭证。
     *
     * @param username 用户名
     * @return 密码凭证实体
     */
    public UserCredentialEntity findByUsername(String username) {
        return credentialMapper.selectOne(Wrappers.<UserCredentialEntity>lambdaQuery()
                .eq(UserCredentialEntity::getUsername, normalize(username))
                .eq(UserCredentialEntity::getStatus, 1));
    }

    /**
     * 按用户名查找包含停用状态的密码凭证。
     *
     * @param username 用户名
     * @return 密码凭证实体
     */
    public UserCredentialEntity findAnyByUsername(String username) {
        return credentialMapper.selectOne(Wrappers.<UserCredentialEntity>lambdaQuery()
                .eq(UserCredentialEntity::getUsername, normalize(username)));
    }

    /**
     * 按用户ID查找有效密码凭证。
     *
     * @param userId 用户ID
     * @return 密码凭证实体
     */
    public UserCredentialEntity findByUserId(Long userId) {
        return credentialMapper.selectOne(Wrappers.<UserCredentialEntity>lambdaQuery()
                .eq(UserCredentialEntity::getUserId, userId)
                .eq(UserCredentialEntity::getStatus, 1));
    }

    /**
     * 按用户ID查找包含停用状态的密码凭证。
     *
     * @param userId 用户ID
     * @return 密码凭证实体
     */
    private UserCredentialEntity findAnyByUserId(Long userId) {
        return credentialMapper.selectOne(Wrappers.<UserCredentialEntity>lambdaQuery()
                .eq(UserCredentialEntity::getUserId, userId));
    }

    /**
     * 核对密码与哈希是否匹配。
     *
     * @param password 密码
     * @param passwordHash 密码哈希
     * @return 条件是否成立
     */
    public boolean matches(String password, String passwordHash) {
        return passwordEncoder.matches(password, passwordHash);
    }

    /**
     * 记录密码校验失败并更新锁定状态。
     *
     * @param credential 密码凭证实体
     */
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

    /**
     * 清除密码校验失败记录。
     *
     * @param credential 密码凭证实体
     */
    @Transactional
    public void recordSuccess(UserCredentialEntity credential) {
        credential.setFailedCount(0);
        credential.setLockedUntil(null);
        credential.setLastLoginTime(LocalDateTime.now());
        credentialMapper.updateById(credential);
    }

    /**
     * 判断密码凭证是否处于锁定期。
     *
     * @param credential 密码凭证实体
     * @return 条件是否成立
     */
    public boolean isLocked(UserCredentialEntity credential) {
        return credential.getLockedUntil() != null && credential.getLockedUntil().isAfter(LocalDateTime.now());
    }

    /**
     * 构建新的密码凭证。
     *
     * @param userId 用户ID
     * @param username 用户名
     * @param password 密码
     * @return 密码凭证实体
     */
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

    /**
     * 更新密码凭证及绑定信息。
     *
     * @param credential 密码凭证实体
     * @param username 用户名
     * @param password 密码
     * @param userId 用户ID
     */
    private void updateCredential(UserCredentialEntity credential, String username, String password, Long userId) {
        credential.setUsername(username);
        credential.setPasswordHash(passwordEncoder.encode(password));
        credential.setFailedCount(0);
        credential.setLockedUntil(null);
        credential.setStatus(1);
        credential.setModifier(String.valueOf(userId));
    }

    /**
     * 查询并校验启用状态的用户。
     *
     * @param userId 用户ID
     * @return 密码凭证实体
     */
    private UserEntity requireActiveUser(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCodes.USER_DATA_INVALID, "用户状态异常，请重新登录");
        }
        return user;
    }

    /**
     * 规范化输入文本。
     *
     * @param username 用户名
     * @return 处理后的文本
     */
    private String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }
}
