package com.lhj.jizhang.user.service;

import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.entity.BookMemberEntity;
import com.lhj.jizhang.user.mapper.AccountMapper;
import com.lhj.jizhang.user.mapper.BookMapper;
import com.lhj.jizhang.user.mapper.BookMemberMapper;
import com.lhj.jizhang.user.mapper.CategoryMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookRemarkServiceTest {
    /** 同一本账本按登录成员分别返回私有备注，不修改账本名称。 */
    @Test
    void shouldKeepRemarksSeparateForMembers() {
        BookMapper books = mock(BookMapper.class);
        BookMemberMapper members = mock(BookMemberMapper.class);
        BookEntity book = new BookEntity();
        book.setId(7L);
        book.setName("家庭账本");
        book.setStatus(1);
        BookMemberEntity owner = new BookMemberEntity();
        owner.setBookId(7L);
        owner.setRole("OWNER");
        BookMemberEntity guest = new BookMemberEntity();
        guest.setBookId(7L);
        guest.setRole("VIEWER");
        guest.setRemarkName("妈妈");
        when(members.selectList(any())).thenReturn(List.of(owner), List.of(guest));
        when(books.selectByIds(any())).thenReturn(List.of(book));
        BookService service = new BookService(books, members, mock(CategoryMapper.class),
                mock(AccountMapper.class), mock(BookAccessService.class));

        assertNull(service.list(1L).getFirst().remarkName());
        assertEquals("妈妈", service.list(2L).getFirst().remarkName());
        assertEquals("家庭账本", book.getName());
    }

    /** 普通成员也可设置私有备注，留空只清除自己的备注。 */
    @Test
    void shouldClearOnlyCurrentMemberRemark() {
        BookMapper books = mock(BookMapper.class);
        BookMemberMapper members = mock(BookMemberMapper.class);
        BookAccessService access = mock(BookAccessService.class);
        BookEntity book = new BookEntity();
        book.setId(7L);
        book.setName("家庭账本");
        book.setStatus(1);
        BookMemberEntity guest = new BookMemberEntity();
        guest.setId(12L);
        guest.setRole("VIEWER");
        guest.setRemarkName("旧备注");
        when(access.requireMember(2L, 7L)).thenReturn(guest);
        when(books.selectById(7L)).thenReturn(book);
        BookService service = new BookService(books, members, mock(CategoryMapper.class),
                mock(AccountMapper.class), access);

        assertNull(service.updateRemark(2L, 7L, "  ").remarkName());
        assertEquals("旧备注", guest.getRemarkName());
        assertEquals("家庭账本", book.getName());
        verify(members).updateRemark(12L, null, "2");
        verify(books, never()).updateById(any(BookEntity.class));
    }

    /** 非账本成员不能修改任何人的备注。 */
    @Test
    void shouldRejectRemarkUpdateForNonMember() {
        BookMemberMapper members = mock(BookMemberMapper.class);
        BookAccessService access = mock(BookAccessService.class);
        when(access.requireMember(3L, 7L)).thenThrow(new BusinessException("BOOK_ACCESS_DENIED", "无权访问"));
        BookService service = new BookService(mock(BookMapper.class), members, mock(CategoryMapper.class),
                mock(AccountMapper.class), access);

        assertThrows(BusinessException.class, () -> service.updateRemark(3L, 7L, "备注"));
        verify(members, never()).updateById(any(BookMemberEntity.class));
    }
}
