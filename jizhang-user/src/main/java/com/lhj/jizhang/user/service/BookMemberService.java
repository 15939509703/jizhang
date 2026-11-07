package com.lhj.jizhang.user.service;

import com.lhj.jizhang.user.model.BookMenu;
import com.lhj.jizhang.user.model.BookPermission;

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
        bookAccessService.requireMenu(userId, bookId, BookMenu.FAMILY_SHARING);
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
        bookAccessService.requireAdmin(actorId, bookId);
        bookAccessService.requirePermission(actorId, bookId, BookPermission.MANAGE_MEMBERS);
        throw upgradeRequired();
    }

    @Transactional
    public BookMemberOutDTO updateRole(Long actorId, Long bookId, Long memberId, BookMemberRoleInDTO input) {
        bookAccessService.requireAdmin(actorId, bookId);
        bookAccessService.requirePermission(actorId, bookId, BookPermission.MANAGE_MEMBERS);
        throw upgradeRequired();
    }

    @Transactional
    public void remove(Long actorId, Long bookId, Long memberId) {
        BookMemberEntity actor = bookAccessService.requireAdmin(actorId, bookId);
        bookAccessService.requirePermission(actorId, bookId, BookPermission.MANAGE_MEMBERS);
        BookMemberEntity member = requireManagedMember(bookId, memberId);
        validateManagedRole(actor, member.getRole(), member);
        if (bookMemberMapper.deactivate(memberId, bookId, member.getPolicyVersion(), actor.getRole(),
                String.valueOf(actorId)) != 1) {
            throw new BusinessException(ErrorCodes.MEMBER_POLICY_CONFLICT, "成员状态已变化，请刷新后重试",
                    org.springframework.http.HttpStatus.CONFLICT);
        }
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

    private BookMemberEntity requireManagedMember(Long bookId, Long memberId) {
        BookMemberEntity member = bookMemberMapper.selectById(memberId);
        if (member == null || !bookId.equals(member.getBookId()) || !Integer.valueOf(1).equals(member.getStatus())) {
            throw new BusinessException(ErrorCodes.MEMBER_INVALID, "成员不存在或已退出");
        }
        return member;
    }

    /** 引导旧写入口升级，避免创建缺失策略的成员。 */
    private BusinessException upgradeRequired() {
        return new BusinessException(ErrorCodes.MEMBER_POLICY_UPGRADE_REQUIRED, "请升级客户端并使用完整权限配置",
                org.springframework.http.HttpStatus.CONFLICT);
    }

    private BookMemberOutDTO toOutput(BookMemberEntity member, UserEntity user) {
        return new BookMemberOutDTO(member.getId(), member.getUserId(), user.getUserNo(), user.getNickName(),
                user.getAvatarUrl(), member.getRole(), member.getStatus());
    }
}
