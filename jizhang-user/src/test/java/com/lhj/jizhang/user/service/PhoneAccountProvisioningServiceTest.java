package com.lhj.jizhang.user.service;

import com.lhj.jizhang.security.model.TokenPair;
import com.lhj.jizhang.security.service.TokenService;
import com.lhj.jizhang.user.dto.PasswordCredentialInDTO;
import com.lhj.jizhang.user.dto.WechatLoginOutDTO;
import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.entity.UserAuthEntity;
import com.lhj.jizhang.user.entity.UserEntity;
import com.lhj.jizhang.user.mapper.UserAuthMapper;
import com.lhj.jizhang.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhoneAccountProvisioningServiceTest {
    @Mock
    private UserMapper userMapper;
    @Mock
    private UserAuthMapper userAuthMapper;
    @Mock
    private PasswordCredentialService credentialService;
    @Mock
    private BookService bookService;
    @Mock
    private TokenService tokenService;

    private PhoneAccountProvisioningService service;

    @BeforeEach
    void setUp() {
        service = new PhoneAccountProvisioningService(
                userMapper, userAuthMapper, credentialService, bookService, tokenService);
    }

    @Test
    void createInitializesPhoneUserAndReturnsLoginResult() {
        String phone = "13800138000";
        doAnswer(invocation -> {
            invocation.<UserEntity>getArgument(0).setId(101L);
            return 1;
        }).when(userMapper).insert(any(UserEntity.class));
        BookEntity book = new BookEntity();
        book.setId(201L);
        book.setBookNo("BOOK_001");
        book.setName("个人账本");
        when(bookService.createDefault(101L)).thenReturn(book);
        when(tokenService.issue(101L, 1)).thenReturn(new TokenPair("access", "refresh", 1800));

        WechatLoginOutDTO output = service.create(phone, "strong-password");

        assertEquals(101L, output.user().id());
        assertEquals("用户8000", output.user().nickName());
        assertEquals(201L, output.defaultBook().id());
        verify(credentialService).bind(101L, new PasswordCredentialInDTO(phone, "strong-password"));
        ArgumentCaptor<UserAuthEntity> authCaptor = ArgumentCaptor.forClass(UserAuthEntity.class);
        verify(userAuthMapper).insert(authCaptor.capture());
        assertEquals("PHONE", authCaptor.getValue().getProvider());
        assertEquals(phone, authCaptor.getValue().getOpenid());
    }
}
