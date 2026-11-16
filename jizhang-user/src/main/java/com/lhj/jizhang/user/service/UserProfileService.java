package com.lhj.jizhang.user.service;

import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.user.dto.LoginUserOutDTO;
import com.lhj.jizhang.user.dto.UserProfileUpdateInDTO;
import com.lhj.jizhang.user.entity.UserEntity;
import com.lhj.jizhang.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {
    private final UserMapper userMapper;

    /**
     * 初始化用户资料服务。
     *
     * @param userMapper 数据访问器
     */
    public UserProfileService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 查询用户资料。
     *
     * @param userId 用户ID
     * @return 用户资料输出对象
     */
    public LoginUserOutDTO get(Long userId) {
        return toOutput(requireActiveUser(userId));
    }

    /**
     * 更新用户资料。
     *
     * @param userId 用户ID
     * @param input 请求参数
     * @return 用户资料输出对象
     */
    @Transactional
    public LoginUserOutDTO update(Long userId, UserProfileUpdateInDTO input) {
        UserEntity user = requireActiveUser(userId);
        user.setNickName(input.nickName().trim());
        user.setModifier(String.valueOf(userId));
        userMapper.updateById(user);
        return toOutput(user);
    }

    /**
     * 查询并校验启用状态的用户。
     *
     * @param userId 用户ID
     * @return 用户资料实体
     */
    private UserEntity requireActiveUser(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCodes.USER_DATA_INVALID, "用户状态异常，请重新登录");
        }
        return user;
    }

    /**
     * 转换为用户资料输出对象。
     *
     * @param user 用户实体
     * @return 用户资料输出对象
     */
    private LoginUserOutDTO toOutput(UserEntity user) {
        return new LoginUserOutDTO(user.getId(), user.getUserNo(), user.getNickName(), UserAvatarService.publicUrl(user));
    }
}
