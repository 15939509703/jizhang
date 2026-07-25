package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.user.dto.PasswordCredentialInDTO;
import com.lhj.jizhang.user.dto.PhoneBindInDTO;
import com.lhj.jizhang.user.dto.PhoneBindingOutDTO;
import com.lhj.jizhang.user.entity.UserAuthEntity;
import com.lhj.jizhang.user.entity.UserCredentialEntity;
import com.lhj.jizhang.user.mapper.UserAuthMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhoneBindingServiceTest {
    private static final Long USER_ID = 7L;
    private static final String PHONE = "13800138000";
    private static final String PASSWORD = "strong-password";

    @Mock
    private UserAuthMapper userAuthMapper;
    @Mock
    private PasswordCredentialService credentialService;

    private PhoneBindingService service;

    @BeforeEach
    void setUp() {
        service = new PhoneBindingService(userAuthMapper, credentialService);
    }

    @Test
    void bindCreatesPhoneAuthAndPasswordCredentialForWechatUser() {
        when(userAuthMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(userAuthMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(credentialService.findAnyByUsername(PHONE)).thenReturn(null);
        doAnswer(invocation -> {
            invocation.<UserAuthEntity>getArgument(0).setId(99L);
            return 1;
        }).when(userAuthMapper).insert(any(UserAuthEntity.class));

        service.bind(USER_ID, new PhoneBindInDTO(PHONE, PASSWORD));

        ArgumentCaptor<UserAuthEntity> authCaptor = ArgumentCaptor.forClass(UserAuthEntity.class);
        verify(userAuthMapper).insert(authCaptor.capture());
        assertEquals(USER_ID, authCaptor.getValue().getUserId());
        assertEquals("PHONE", authCaptor.getValue().getProvider());
        assertEquals(PHONE, authCaptor.getValue().getOpenid());
        verify(credentialService).bind(USER_ID, new PasswordCredentialInDTO(PHONE, PASSWORD));
    }

    @Test
    void bindRejectsPhoneOwnedByAnotherUser() {
        UserAuthEntity existing = new UserAuthEntity();
        existing.setUserId(8L);
        existing.setProvider("PHONE");
        existing.setOpenid(PHONE);
        existing.setStatus(1);
        when(userAuthMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(userAuthMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.bind(USER_ID, new PhoneBindInDTO(PHONE, PASSWORD)));

        assertEquals(ErrorCodes.PHONE_ALREADY_REGISTERED, exception.getCode());
        verify(credentialService, never()).bind(any(), any());
    }

    @Test
    void bindRejectsNonWechatUser() {
        when(userAuthMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.bind(USER_ID, new PhoneBindInDTO(PHONE, PASSWORD)));

        assertEquals(ErrorCodes.PHONE_BINDING_INVALID, exception.getCode());
        verify(userAuthMapper, never()).selectOne(any(Wrapper.class));
        verify(credentialService, never()).bind(any(), any());
    }

    @Test
    void statusReturnsBoundPhone() {
        UserAuthEntity auth = new UserAuthEntity();
        auth.setUserId(USER_ID);
        auth.setProvider("PHONE");
        auth.setOpenid(PHONE);
        auth.setStatus(1);
        UserCredentialEntity credential = new UserCredentialEntity();
        credential.setUserId(USER_ID);
        credential.setUsername(PHONE);
        credential.setStatus(1);
        when(userAuthMapper.selectOne(any(Wrapper.class))).thenReturn(auth);
        when(credentialService.findByUsername(PHONE)).thenReturn(credential);

        PhoneBindingOutDTO output = service.status(USER_ID);

        assertEquals(true, output.bound());
        assertEquals(PHONE, output.phone());
    }

    @Test
    void statusRequiresMatchingPasswordCredential() {
        UserAuthEntity auth = new UserAuthEntity();
        auth.setUserId(USER_ID);
        auth.setProvider("PHONE");
        auth.setOpenid(PHONE);
        auth.setStatus(1);
        when(userAuthMapper.selectOne(any(Wrapper.class))).thenReturn(auth);
        when(credentialService.findByUsername(PHONE)).thenReturn(null);

        PhoneBindingOutDTO output = service.status(USER_ID);

        assertEquals(false, output.bound());
        assertEquals(PHONE, output.phone());
    }
}
