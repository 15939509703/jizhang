package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.user.entity.BookMemberEntity;
import com.lhj.jizhang.user.mapper.BookMemberMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookAccessServiceTest {
    @Test
    void shouldRejectNonMember() {
        BookMemberMapper mapper = mock(BookMemberMapper.class);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);
        BookAccessService service = new BookAccessService(mapper);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.requireMember(7L, 1L));

        assertEquals(ErrorCodes.BOOK_ACCESS_DENIED, exception.getCode());
    }

    @Test
    void shouldRejectViewerWriteAccess() {
        BookMemberMapper mapper = mock(BookMemberMapper.class);
        BookMemberEntity member = new BookMemberEntity();
        member.setRole("VIEWER");
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(member);
        BookAccessService service = new BookAccessService(mapper);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.requireWritable(7L, 1L));

        assertEquals(ErrorCodes.BOOK_ACCESS_DENIED, exception.getCode());
    }
}
