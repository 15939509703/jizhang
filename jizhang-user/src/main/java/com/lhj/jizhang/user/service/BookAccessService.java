package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.user.entity.BookMemberEntity;
import com.lhj.jizhang.user.mapper.BookMemberMapper;
import com.lhj.jizhang.user.model.BookPermission;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class BookAccessService {
    private final BookMemberMapper bookMemberMapper;
    private final ObjectMapper objectMapper;

    @Autowired
    public BookAccessService(BookMemberMapper bookMemberMapper, ObjectMapper objectMapper) {
        this.bookMemberMapper = bookMemberMapper;
        this.objectMapper = objectMapper;
    }

    BookAccessService(BookMemberMapper bookMemberMapper) {
        this(bookMemberMapper, new ObjectMapper());
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
        return requirePermission(userId, bookId, BookPermission.CREATE_TRANSACTIONS);
    }

    public BookMemberEntity requireAdmin(Long userId, Long bookId) {
        BookMemberEntity member = requireMember(userId, bookId);
        if (!"OWNER".equals(member.getRole()) && !"ADMIN".equals(member.getRole())) {
            throw new BusinessException(ErrorCodes.BOOK_ACCESS_DENIED, "仅账本所有者或管理员可执行此操作",
                    HttpStatus.FORBIDDEN);
        }
        return member;
    }

    public BookMemberEntity requireOwner(Long userId, Long bookId) {
        BookMemberEntity member = requireMember(userId, bookId);
        if (!"OWNER".equals(member.getRole())) {
            throw new BusinessException(ErrorCodes.BOOK_ACCESS_DENIED, "仅账本所有者可执行此操作",
                    HttpStatus.FORBIDDEN);
        }
        return member;
    }

    public BookMemberEntity requirePermission(Long userId, Long bookId, BookPermission permission) {
        BookMemberEntity member = requireMember(userId, bookId);
        if (!hasPermission(member, permission)) {
            throw new BusinessException(ErrorCodes.BOOK_ACCESS_DENIED, "当前成员无此操作权限", HttpStatus.FORBIDDEN);
        }
        return member;
    }

    public boolean hasPermission(BookMemberEntity member, BookPermission permission) {
        if ("OWNER".equals(member.getRole())) return true;
        Boolean override = permissionOverride(member.getPermissions(), permission.name());
        if (override != null) return override;
        return switch (member.getRole()) {
            case "ADMIN" -> true;
            case "MEMBER" -> permission != BookPermission.MANAGE_MEMBERS;
            case "VIEWER" -> permission == BookPermission.VIEW_TRANSACTIONS;
            default -> false;
        };
    }

    private Boolean permissionOverride(String json, String key) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<java.util.Map<String, Boolean>>() {}).get(key);
        } catch (Exception ignored) {
            return null;
        }
    }
}
