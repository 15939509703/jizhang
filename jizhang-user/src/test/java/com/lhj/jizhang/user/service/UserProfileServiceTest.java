package com.lhj.jizhang.user.service;

import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.user.dto.LoginUserOutDTO;
import com.lhj.jizhang.user.dto.UserProfileUpdateInDTO;
import com.lhj.jizhang.user.entity.UserEntity;
import com.lhj.jizhang.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserProfileServiceTest {
    @Test
    void shouldGetCurrentUserProfile() {
        UserMapper userMapper = mock(UserMapper.class);
        when(userMapper.selectById(7L)).thenReturn(activeUser());
        UserProfileService service = new UserProfileService(userMapper);

        LoginUserOutDTO result = service.get(7L);

        assertEquals(7L, result.id());
        assertEquals("USR_7", result.userNo());
        assertEquals("原昵称", result.nickName());
    }

    @Test
    void shouldTrimAndUpdateNickname() {
        UserMapper userMapper = mock(UserMapper.class);
        UserEntity user = activeUser();
        when(userMapper.selectById(7L)).thenReturn(user);
        UserProfileService service = new UserProfileService(userMapper);

        LoginUserOutDTO result = service.update(7L, new UserProfileUpdateInDTO("  新昵称  "));

        assertEquals("新昵称", result.nickName());
        assertEquals("新昵称", user.getNickName());
        assertEquals("7", user.getModifier());
        verify(userMapper).updateById(user);
    }

    @Test
    void shouldRejectDisabledUser() {
        UserMapper userMapper = mock(UserMapper.class);
        UserEntity user = activeUser();
        user.setStatus(0);
        when(userMapper.selectById(7L)).thenReturn(user);
        UserProfileService service = new UserProfileService(userMapper);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.get(7L));

        assertEquals(ErrorCodes.USER_DATA_INVALID, exception.getCode());
    }

    private UserEntity activeUser() {
        UserEntity user = new UserEntity();
        user.setId(7L);
        user.setUserNo("USR_7");
        user.setNickName("原昵称");
        user.setAvatarUrl("https://example.com/avatar.png");
        user.setStatus(1);
        return user;
    }
}
