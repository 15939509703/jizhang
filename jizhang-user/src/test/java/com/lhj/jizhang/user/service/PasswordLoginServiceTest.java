package com.lhj.jizhang.user.service;

import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.security.service.TokenService;
import com.lhj.jizhang.user.dto.PasswordLoginInDTO;
import com.lhj.jizhang.user.mapper.BookMapper;
import com.lhj.jizhang.user.mapper.BookMemberMapper;
import com.lhj.jizhang.user.mapper.UserAuthMapper;
import com.lhj.jizhang.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PasswordLoginServiceTest {
    @Test
    void loginUsesGenericErrorWhenCredentialDoesNotExist() {
        PasswordCredentialService credentialService = mock(PasswordCredentialService.class);
        when(credentialService.findByUsername("missing")).thenReturn(null);
        PasswordLoginService service = new PasswordLoginService(
                credentialService,
                mock(UserMapper.class),
                mock(UserAuthMapper.class),
                mock(BookMapper.class),
                mock(BookMemberMapper.class),
                mock(BookService.class),
                mock(TokenService.class)
        );

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.login(new PasswordLoginInDTO("missing", "wrong-password")));

        assertEquals(ErrorCodes.PASSWORD_LOGIN_FAILED, exception.getCode());
        assertEquals("账号或密码错误", exception.getMessage());
    }
}
