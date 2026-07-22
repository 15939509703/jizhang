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

    public UserProfileService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public LoginUserOutDTO get(Long userId) {
        return toOutput(requireActiveUser(userId));
    }

    @Transactional
    public LoginUserOutDTO update(Long userId, UserProfileUpdateInDTO input) {
        UserEntity user = requireActiveUser(userId);
        user.setNickName(input.nickName().trim());
        user.setModifier(String.valueOf(userId));
        userMapper.updateById(user);
        return toOutput(user);
    }

    private UserEntity requireActiveUser(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCodes.USER_DATA_INVALID, "用户状态异常，请重新登录");
        }
        return user;
    }

    private LoginUserOutDTO toOutput(UserEntity user) {
        return new LoginUserOutDTO(user.getId(), user.getUserNo(), user.getNickName(), user.getAvatarUrl());
    }
}
