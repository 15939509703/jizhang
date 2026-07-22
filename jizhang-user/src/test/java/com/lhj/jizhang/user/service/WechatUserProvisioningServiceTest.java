package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lhj.jizhang.user.dto.WechatLoginInDTO;
import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.entity.BookMemberEntity;
import com.lhj.jizhang.user.entity.UserAuthEntity;
import com.lhj.jizhang.user.entity.UserEntity;
import com.lhj.jizhang.user.mapper.BookMapper;
import com.lhj.jizhang.user.mapper.BookMemberMapper;
import com.lhj.jizhang.user.mapper.UserAuthMapper;
import com.lhj.jizhang.user.mapper.UserMapper;
import com.lhj.jizhang.user.model.LoginUserContext;
import com.lhj.jizhang.user.model.WechatSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WechatUserProvisioningServiceTest {
    @Mock
    private UserMapper userMapper;
    @Mock
    private UserAuthMapper userAuthMapper;
    @Mock
    private BookMapper bookMapper;
    @Mock
    private BookMemberMapper bookMemberMapper;
    @Mock
    private BookService bookService;

    private WechatUserProvisioningService service;

    @BeforeEach
    void setUp() {
        service = new WechatUserProvisioningService(
                userMapper,
                userAuthMapper,
                bookMapper,
                bookMemberMapper,
                bookService
        );
    }

    @Test
    void shouldInitializeNewWechatUser() {
        when(userAuthMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        doAssignUserId();
        when(bookService.createDefault(101L)).thenReturn(activeBook(201L));

        LoginUserContext context = service.findOrCreate(
                new WechatSession("openid-1001", "unionid-1001", "session-key"),
                new WechatLoginInDTO("code", "小明", "https://example.com/avatar.png")
        );

        assertEquals(101L, context.user().getId());
        assertEquals("小明", context.user().getNickName());
        assertEquals("openid-1001", context.auth().getOpenid());
        assertEquals(201L, context.defaultBook().getId());
        verify(bookService).createDefault(101L);
    }

    @Test
    void shouldLoginExistingWechatUserWithoutReinitializingData() {
        UserAuthEntity auth = activeAuth(101L);
        UserEntity user = activeUser(101L);
        BookMemberEntity member = activeMember(201L, 101L);
        BookEntity book = activeBook(201L);
        when(userAuthMapper.selectOne(any(Wrapper.class))).thenReturn(auth);
        when(userMapper.selectById(101L)).thenReturn(user);
        when(bookMemberMapper.selectOne(any(Wrapper.class))).thenReturn(member);
        when(bookMapper.selectById(201L)).thenReturn(book);

        LoginUserContext context = service.findOrCreate(
                new WechatSession("openid-1001", null, "new-session-key"),
                new WechatLoginInDTO("code", "新昵称", "https://example.com/new-avatar.png")
        );

        assertSame(user, context.user());
        assertSame(auth, context.auth());
        assertSame(book, context.defaultBook());
        assertEquals("旧昵称", user.getNickName());
        assertEquals("https://example.com/new-avatar.png", user.getAvatarUrl());
        verify(userMapper).updateById(user);
        verify(bookService, never()).createDefault(any());
    }

    private void doAssignUserId() {
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.<UserEntity>getArgument(0).setId(101L);
            return 1;
        }).when(userMapper).insert(any(UserEntity.class));
    }

    private UserAuthEntity activeAuth(Long userId) {
        UserAuthEntity auth = new UserAuthEntity();
        auth.setUserId(userId);
        auth.setProvider("WECHAT");
        auth.setOpenid("openid-1001");
        auth.setSessionVersion(1);
        auth.setStatus(1);
        return auth;
    }

    private UserEntity activeUser(Long userId) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setNickName("旧昵称");
        user.setStatus(1);
        return user;
    }

    private BookMemberEntity activeMember(Long bookId, Long userId) {
        BookMemberEntity member = new BookMemberEntity();
        member.setId(301L);
        member.setBookId(bookId);
        member.setUserId(userId);
        member.setStatus(1);
        return member;
    }

    private BookEntity activeBook(Long bookId) {
        BookEntity book = new BookEntity();
        book.setId(bookId);
        book.setBookNo("BOOK_001");
        book.setName("个人账本");
        return book;
    }
}
