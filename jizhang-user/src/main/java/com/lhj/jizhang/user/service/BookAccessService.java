package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.user.entity.BookMemberEntity;
import com.lhj.jizhang.user.mapper.BookMemberMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class BookAccessService {
    private final BookMemberMapper bookMemberMapper;

    public BookAccessService(BookMemberMapper bookMemberMapper) {
        this.bookMemberMapper = bookMemberMapper;
    }

    public BookMemberEntity requireMember(Long userId, Long bookId) {
        BookMemberEntity member = bookMemberMapper.selectOne(Wrappers.<BookMemberEntity>lambdaQuery()
                .eq(BookMemberEntity::getBookId, bookId)
                .eq(BookMemberEntity::getUserId, userId)
                .eq(BookMemberEntity::getStatus, 1));
        if (member == null) {
            throw new BusinessException(ErrorCodes.BOOK_ACCESS_DENIED, "无权访问该账本", HttpStatus.FORBIDDEN);
        }
        return member;
    }

    public BookMemberEntity requireWritable(Long userId, Long bookId) {
        BookMemberEntity member = requireMember(userId, bookId);
        if ("VIEWER".equals(member.getRole())) {
            throw new BusinessException(ErrorCodes.BOOK_ACCESS_DENIED, "当前成员仅有查看权限", HttpStatus.FORBIDDEN);
        }
        return member;
    }
}
