package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.user.dto.PasswordLoginInDTO;
import com.lhj.jizhang.user.dto.PhoneRegisterInDTO;
import com.lhj.jizhang.user.dto.WechatLoginOutDTO;
import com.lhj.jizhang.user.entity.UserAuthEntity;
import com.lhj.jizhang.user.entity.UserCredentialEntity;
import com.lhj.jizhang.user.mapper.UserAuthMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhoneRegistrationServiceTest {
    private static final String PHONE = "13800138000";
    private static final String PASSWORD = "strong-password";

    @Mock
    private UserAuthMapper userAuthMapper;
    @Mock
    private PasswordCredentialService credentialService;
    @Mock
    private PasswordLoginService passwordLoginService;
    @Mock
    private PhoneAccountProvisioningService provisioningService;

    private PhoneRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new PhoneRegistrationService(
                userAuthMapper, credentialService, passwordLoginService, provisioningService);
    }

    @Test
    void registerCreatesIndependentAccountWhenPhoneHasNoBinding() {
        WechatLoginOutDTO expected = loginOutput();
        when(userAuthMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(credentialService.findByUsername(PHONE)).thenReturn(null);
        when(provisioningService.create(PHONE, PASSWORD)).thenReturn(expected);

        WechatLoginOutDTO output = service.register(new PhoneRegisterInDTO(PHONE, PASSWORD));

        assertSame(expected, output);
        verify(provisioningService).create(PHONE, PASSWORD);
        verify(passwordLoginService, never()).login(any());
    }

    @Test
    void registerLogsIntoWechatAccountWhenPhoneIsBound() {
        UserAuthEntity phoneAuth = activePhoneAuth(88L);
        UserCredentialEntity credential = credential(88L);
        WechatLoginOutDTO expected = loginOutput();
        when(userAuthMapper.selectOne(any(Wrapper.class))).thenReturn(phoneAuth);
        when(credentialService.findByUsername(PHONE)).thenReturn(credential);
        when(userAuthMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(passwordLoginService.login(new PasswordLoginInDTO(PHONE, PASSWORD))).thenReturn(expected);

        WechatLoginOutDTO output = service.register(new PhoneRegisterInDTO(PHONE, PASSWORD));

        assertSame(expected, output);
        verify(passwordLoginService).login(new PasswordLoginInDTO(PHONE, PASSWORD));
        verify(provisioningService, never()).create(any(), any());
    }

    @Test
    void registerRejectsInconsistentWechatPhoneBinding() {
        when(userAuthMapper.selectOne(any(Wrapper.class))).thenReturn(activePhoneAuth(88L));
        when(credentialService.findByUsername(PHONE)).thenReturn(null);
        when(userAuthMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.register(new PhoneRegisterInDTO(PHONE, PASSWORD)));

        assertEquals(ErrorCodes.PHONE_BINDING_INVALID, exception.getCode());
        verify(provisioningService, never()).create(any(), any());
        verify(passwordLoginService, never()).login(any());
    }

    @Test
    void registerRejectsExistingPhoneOnlyAccount() {
        when(userAuthMapper.selectOne(any(Wrapper.class))).thenReturn(activePhoneAuth(88L));
        when(credentialService.findByUsername(PHONE)).thenReturn(credential(88L));
        when(userAuthMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.register(new PhoneRegisterInDTO(PHONE, PASSWORD)));

        assertEquals(ErrorCodes.PHONE_ALREADY_REGISTERED, exception.getCode());
        verify(provisioningService, never()).create(any(), any());
    }

    private UserAuthEntity activePhoneAuth(Long userId) {
        UserAuthEntity auth = new UserAuthEntity();
        auth.setUserId(userId);
        auth.setProvider("PHONE");
        auth.setOpenid(PHONE);
        auth.setStatus(1);
        return auth;
    }

    private UserCredentialEntity credential(Long userId) {
        UserCredentialEntity credential = new UserCredentialEntity();
        credential.setUserId(userId);
        credential.setUsername(PHONE);
        credential.setStatus(1);
        return credential;
    }

    private WechatLoginOutDTO loginOutput() {
        return new WechatLoginOutDTO("access", "refresh", 1800, null, null);
    }
}
