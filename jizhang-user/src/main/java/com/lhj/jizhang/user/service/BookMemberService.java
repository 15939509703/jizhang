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

    /**
     * 初始化账本成员服务。
     *
     * @param bookMemberMapper 数据访问器
     * @param userMapper 数据访问器
     * @param bookAccessService 关联服务
     */
    public BookMemberService(BookMemberMapper bookMemberMapper, UserMapper userMapper,
                             BookAccessService bookAccessService) {
        this.bookMemberMapper = bookMemberMapper;
        this.userMapper = userMapper;
        this.bookAccessService = bookAccessService;
    }

    /**
     * 查询账本成员列表。
     *
     * @param userId 用户ID
     * @param bookId 账本ID
     * @return 账本成员列表
     */
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

    /**
     * 邀请账本成员。
     *
     * @param actorId 操作人ID
     * @param bookId 账本ID
     * @param input 请求参数
     * @return 账本成员输出对象
     */
    @Transactional
    public BookMemberOutDTO invite(Long actorId, Long bookId, BookMemberInviteInDTO input) {
        bookAccessService.requireAdmin(actorId, bookId);
        bookAccessService.requirePermission(actorId, bookId, BookPermission.MANAGE_MEMBERS);
        throw upgradeRequired();
    }

    /**
     * 更新角色。
     *
     * @param actorId 操作人ID
     * @param bookId 账本ID
     * @param memberId 成员ID
     * @param input 请求参数
     * @return 账本成员输出对象
     */
    @Transactional
    public BookMemberOutDTO updateRole(Long actorId, Long bookId, Long memberId, BookMemberRoleInDTO input) {
        bookAccessService.requireAdmin(actorId, bookId);
        bookAccessService.requirePermission(actorId, bookId, BookPermission.MANAGE_MEMBERS);
        throw upgradeRequired();
    }

    /**
     * 删除账本成员。
     *
     * @param actorId 操作人ID
     * @param bookId 账本ID
     * @param memberId 成员ID
     */
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

    /**
     * 校验操作者能否设置目标成员角色。
     *
     * @param actor 操作人成员实体
     * @param nextRole 待设置的成员角色
     * @param target 目标成员实体
     */
    private void validateManagedRole(BookMemberEntity actor, String nextRole, BookMemberEntity target) {
        if (target != null && "OWNER".equals(target.getRole())) {
            throw new BusinessException(ErrorCodes.MEMBER_INVALID, "不能修改账本所有者");
        }
        if ("ADMIN".equals(actor.getRole())
                && ("ADMIN".equals(nextRole) || target != null && "ADMIN".equals(target.getRole()))) {
            throw new BusinessException(ErrorCodes.BOOK_ACCESS_DENIED, "管理员无权管理其他管理员");
        }
    }

    /**
     * 查询并校验可管理的账本成员。
     *
     * @param bookId 账本ID
     * @param memberId 成员ID
     * @return 账本成员实体
     */
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

    /**
     * 转换为账本成员输出对象。
     *
     * @param member 成员实体
     * @param user 用户实体
     * @return 账本成员输出对象
     */
    private BookMemberOutDTO toOutput(BookMemberEntity member, UserEntity user) {
        return new BookMemberOutDTO(member.getId(), member.getUserId(), user.getUserNo(), user.getNickName(),
                user.getAvatarUrl(), member.getRole(), member.getStatus());
    }
}
