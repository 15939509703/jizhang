package com.lhj.jizhang.user.service;

import com.lhj.jizhang.user.dto.PasswordCredentialInDTO;
import com.lhj.jizhang.user.entity.UserCredentialEntity;
import com.lhj.jizhang.user.entity.UserEntity;
import com.lhj.jizhang.user.mapper.UserCredentialMapper;
import com.lhj.jizhang.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordCredentialServiceTest {
    @Test
    void bindCreatesHashedCredentialForCurrentUser() {
        UserCredentialMapper credentialMapper = mock(UserCredentialMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        UserEntity user = new UserEntity();
        user.setId(7L);
        user.setStatus(1);
        when(userMapper.selectById(7L)).thenReturn(user);
        when(credentialMapper.selectOne(any())).thenReturn(null);
        PasswordCredentialService service = new PasswordCredentialService(credentialMapper, userMapper);

        service.bind(7L, new PasswordCredentialInDTO("My.Account", "strong-password"));

        ArgumentCaptor<UserCredentialEntity> captor = ArgumentCaptor.forClass(UserCredentialEntity.class);
        verify(credentialMapper).insert(captor.capture());
        UserCredentialEntity credential = captor.getValue();
        assertEquals(7L, credential.getUserId());
        assertEquals("my.account", credential.getUsername());
        assertNotEquals("strong-password", credential.getPasswordHash());
        assertTrue(new BCryptPasswordEncoder().matches("strong-password", credential.getPasswordHash()));
    }
}
