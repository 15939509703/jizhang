package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.user.dto.BookMemberInviteInDTO;
import com.lhj.jizhang.user.dto.BookMemberOutDTO;
import com.lhj.jizhang.user.dto.BookMemberRoleInDTO;
import com.lhj.jizhang.user.entity.BookMemberEntity;
import com.lhj.jizhang.user.entity.UserEntity;
import com.lhj.jizhang.user.mapper.BookMemberMapper;
import com.lhj.jizhang.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BookMemberService {
    private final BookMemberMapper bookMemberMapper;
    private final UserMapper userMapper;
    private final BookAccessService bookAccessService;

    public BookMemberService(BookMemberMapper bookMemberMapper, UserMapper userMapper,
                             BookAccessService bookAccessService) {
        this.bookMemberMapper = bookMemberMapper;
        this.userMapper = userMapper;
        this.bookAccessService = bookAccessService;
    }

    public List<BookMemberOutDTO> list(Long userId, Long bookId) {
        bookAccessService.requireMember(userId, bookId);
        List<BookMemberEntity> members = bookMemberMapper.selectList(Wrappers.<BookMemberEntity>lambdaQuery()
                .eq(BookMemberEntity::getBookId, bookId)
                .eq(BookMemberEntity::getStatus, 1)
                .orderByAsc(BookMemberEntity::getId));
        Map<Long, UserEntity> users = userMapper.selectByIds(members.stream().map(BookMemberEntity::getUserId).toList())
                .stream().collect(Collectors.toMap(UserEntity::getId, Function.identity()));
        return members.stream().map(member -> toOutput(member, users.get(member.getUserId()))).toList();
    }

    @Transactional
    public BookMemberOutDTO invite(Long actorId, Long bookId, BookMemberInviteInDTO input) {
        BookMemberEntity actor = bookAccessService.requireAdmin(actorId, bookId);
        UserEntity user = findUser(input.userNo());
        BookMemberEntity member = findMembership(bookId, user.getId());
        validateManagedRole(actor, input.role(), member);
        if (member == null) {
            member = buildMember(bookId, user.getId(), actorId, input.role());
            bookMemberMapper.insert(member);
        } else {
            if ("OWNER".equals(member.getRole())) {
                throw new BusinessException(ErrorCodes.MEMBER_INVALID, "账本所有者不能被重复邀请");
            }
            member.setRole(input.role());
            member.setStatus(1);
            member.setInvitedBy(actorId);
            member.setJoinedTime(LocalDateTime.now(ZoneOffset.UTC));
            member.setModifier(String.valueOf(actorId));
            bookMemberMapper.updateById(member);
        }
        return toOutput(member, user);
    }

    @Transactional
    public BookMemberOutDTO updateRole(Long actorId, Long bookId, Long memberId, BookMemberRoleInDTO input) {
        BookMemberEntity actor = bookAccessService.requireAdmin(actorId, bookId);
        BookMemberEntity member = requireManagedMember(bookId, memberId);
        validateManagedRole(actor, input.role(), member);
        member.setRole(input.role());
        member.setModifier(String.valueOf(actorId));
        bookMemberMapper.updateById(member);
        return toOutput(member, userMapper.selectById(member.getUserId()));
    }

    @Transactional
    public void remove(Long actorId, Long bookId, Long memberId) {
        BookMemberEntity actor = bookAccessService.requireAdmin(actorId, bookId);
        BookMemberEntity member = requireManagedMember(bookId, memberId);
        validateManagedRole(actor, member.getRole(), member);
        member.setStatus(0);
        member.setModifier(String.valueOf(actorId));
        bookMemberMapper.updateById(member);
    }

    private void validateManagedRole(BookMemberEntity actor, String nextRole, BookMemberEntity target) {
        if (target != null && "OWNER".equals(target.getRole())) {
            throw new BusinessException(ErrorCodes.MEMBER_INVALID, "不能修改账本所有者");
        }
        if ("ADMIN".equals(actor.getRole())
                && ("ADMIN".equals(nextRole) || target != null && "ADMIN".equals(target.getRole()))) {
            throw new BusinessException(ErrorCodes.BOOK_ACCESS_DENIED, "管理员无权管理其他管理员");
        }
    }

    private UserEntity findUser(String userNo) {
        UserEntity user = userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getUserNo, userNo.trim()).eq(UserEntity::getStatus, 1));
        if (user == null) {
            throw new BusinessException(ErrorCodes.MEMBER_INVALID, "未找到该用户编号");
        }
        return user;
    }

    private BookMemberEntity findMembership(Long bookId, Long userId) {
        return bookMemberMapper.selectOne(Wrappers.<BookMemberEntity>lambdaQuery()
                .eq(BookMemberEntity::getBookId, bookId).eq(BookMemberEntity::getUserId, userId));
    }

    private BookMemberEntity requireManagedMember(Long bookId, Long memberId) {
        BookMemberEntity member = bookMemberMapper.selectById(memberId);
        if (member == null || !bookId.equals(member.getBookId()) || member.getStatus() != 1) {
            throw new BusinessException(ErrorCodes.MEMBER_INVALID, "成员不存在或已退出");
        }
        return member;
    }

    private BookMemberEntity buildMember(Long bookId, Long userId, Long actorId, String role) {
        BookMemberEntity member = new BookMemberEntity();
        member.setBookId(bookId);
        member.setUserId(userId);
        member.setRole(role);
        member.setStatus(1);
        member.setInvitedBy(actorId);
        member.setJoinedTime(LocalDateTime.now(ZoneOffset.UTC));
        member.setCreator(String.valueOf(actorId));
        member.setModifier(String.valueOf(actorId));
        return member;
    }

    private BookMemberOutDTO toOutput(BookMemberEntity member, UserEntity user) {
        return new BookMemberOutDTO(member.getId(), member.getUserId(), user.getUserNo(), user.getNickName(),
                user.getAvatarUrl(), member.getRole(), member.getStatus());
    }
}
