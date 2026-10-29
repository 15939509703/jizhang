package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.user.dto.BookInvitationCreateInDTO;
import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.entity.BookInvitationEntity;
import com.lhj.jizhang.user.entity.BookMemberEntity;
import com.lhj.jizhang.user.entity.UserEntity;
import com.lhj.jizhang.user.mapper.BookAuditLogMapper;
import com.lhj.jizhang.user.mapper.BookInvitationMapper;
import com.lhj.jizhang.user.mapper.BookMapper;
import com.lhj.jizhang.user.mapper.BookMemberMapper;
import com.lhj.jizhang.user.mapper.UserMapper;
import com.lhj.jizhang.user.model.BookPermission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FamilySharingServiceTest {
    @Mock private BookInvitationMapper invitationMapper;
    @Mock private BookMemberMapper memberMapper;
    @Mock private BookAuditLogMapper auditMapper;
    @Mock private BookMapper bookMapper;
    @Mock private UserMapper userMapper;
    @Mock private BookAccessService accessService;

    private FamilySharingService service;

    /** 构造被测邀请服务。 */
    @BeforeEach
    void setUp() {
        service = new FamilySharingService(invitationMapper, memberMapper, auditMapper, bookMapper,
                userMapper, accessService, new ObjectMapper());
    }

    /** 链接生成时所选角色由服务端保存，而不是在接受后手动指定。 */
    @Test
    void createInvitationStoresSelectedRole() {
        BookMemberEntity owner = new BookMemberEntity();
        owner.setRole("OWNER");
        when(accessService.requirePermission(1L, 9L, BookPermission.MANAGE_MEMBERS)).thenReturn(owner);
        BookEntity book = new BookEntity();
        book.setName("共享账本");
        when(bookMapper.selectById(9L)).thenReturn(book);
        UserEntity inviter = new UserEntity();
        inviter.setNickName("邀请人");
        when(userMapper.selectById(1L)).thenReturn(inviter);

        var result = service.createInvitation(1L, 9L, new BookInvitationCreateInDTO("VIEWER", 24));

        ArgumentCaptor<BookInvitationEntity> saved = ArgumentCaptor.forClass(BookInvitationEntity.class);
        verify(invitationMapper).insert(saved.capture());
        assertEquals("VIEWER", saved.getValue().getDefaultRole());
        assertEquals("VIEWER", result.role());
        assertNotNull(result.token());
    }

    /** 普通管理员不能通过邀请链接授予其他人管理员权限。 */
    @Test
    void adminCannotCreateAdminInvitation() {
        BookMemberEntity actor = new BookMemberEntity();
        actor.setRole("ADMIN");
        when(accessService.requirePermission(1L, 9L, BookPermission.MANAGE_MEMBERS)).thenReturn(actor);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.createInvitation(1L, 9L, new BookInvitationCreateInDTO("ADMIN", 24)));

        assertEquals(ErrorCodes.BOOK_ACCESS_DENIED, error.getCode());
        verify(invitationMapper, never()).insert(any(BookInvitationEntity.class));
    }

    /** 新成员直接获得邀请链接指定的只读角色。 */
    @Test
    void newMemberReceivesInvitationRole() {
        when(invitationMapper.selectByHashForUpdate(anyString())).thenReturn(activeInvitation("VIEWER"));
        when(memberMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(userMapper.selectById(2L)).thenReturn(user());

        var result = service.accept(2L, "valid-token");

        ArgumentCaptor<BookMemberEntity> saved = ArgumentCaptor.forClass(BookMemberEntity.class);
        verify(memberMapper).insert(saved.capture());
        assertEquals("VIEWER", saved.getValue().getRole());
        assertNull(saved.getValue().getPermissions());
        assertEquals("VIEWER", result.role());
    }

    /** 已有成员再次接受邀请时使用新角色，并清除旧的手动权限覆盖。 */
    @Test
    void existingMemberReceivesInvitationRoleWithoutOldOverrides() {
        BookMemberEntity member = new BookMemberEntity();
        member.setId(3L);
        member.setRole("MEMBER");
        member.setStatus(1);
        member.setPermissions("{\"CREATE_TRANSACTIONS\":true}");
        when(invitationMapper.selectByHashForUpdate(anyString())).thenReturn(activeInvitation("VIEWER"));
        when(memberMapper.selectOne(any(Wrapper.class))).thenReturn(member);
        when(userMapper.selectById(2L)).thenReturn(user());

        var result = service.accept(2L, "valid-token");

        assertEquals("VIEWER", member.getRole());
        assertNull(member.getPermissions());
        assertEquals("VIEWER", result.role());
        verify(memberMapper).updateById(member);
        verify(memberMapper).clearPermissions(3L);
        verify(invitationMapper).updateById(any(BookInvitationEntity.class));
    }

    /** 已停用成员也按新邀请角色恢复，且旧权限覆盖被移除。 */
    @Test
    void inactiveMemberReceivesInvitationRole() {
        BookMemberEntity member = new BookMemberEntity();
        member.setId(3L);
        member.setRole("MEMBER");
        member.setStatus(0);
        member.setPermissions("{\"EXPORT_DATA\":true}");
        when(invitationMapper.selectByHashForUpdate(anyString())).thenReturn(activeInvitation("VIEWER"));
        when(memberMapper.selectOne(any(Wrapper.class))).thenReturn(member);
        when(userMapper.selectById(2L)).thenReturn(user());

        service.accept(2L, "valid-token");

        assertEquals("VIEWER", member.getRole());
        assertEquals(1, member.getStatus());
        assertNotNull(member.getJoinedTime());
        verify(memberMapper).updateById(member);
        verify(memberMapper).clearPermissions(3L);
    }

    /** 账本所有者不能被转发的邀请链接降级。 */
    @Test
    void ownerCannotAcceptInvitation() {
        BookMemberEntity owner = new BookMemberEntity();
        owner.setRole("OWNER");
        owner.setStatus(1);
        when(invitationMapper.selectByHashForUpdate(anyString())).thenReturn(activeInvitation("VIEWER"));
        when(memberMapper.selectOne(any(Wrapper.class))).thenReturn(owner);

        BusinessException error = assertThrows(BusinessException.class, () -> service.accept(2L, "valid-token"));

        assertEquals(ErrorCodes.MEMBER_INVALID, error.getCode());
        verify(invitationMapper, never()).updateById(any(BookInvitationEntity.class));
    }

    /** 已被接受的链接不能再次赋权。 */
    @Test
    void acceptedInvitationCannotBeReused() {
        BookInvitationEntity invitation = activeInvitation("VIEWER");
        invitation.setStatus("ACCEPTED");
        when(invitationMapper.selectByHashForUpdate(anyString())).thenReturn(invitation);

        assertThrows(BusinessException.class, () -> service.accept(2L, "valid-token"));

        verify(memberMapper, never()).insert(any(BookMemberEntity.class));
    }

    /** 超过有效期的链接不能给成员赋权。 */
    @Test
    void expiredInvitationCannotBeAccepted() {
        BookInvitationEntity invitation = activeInvitation("VIEWER");
        invitation.setExpiredTime(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        when(invitationMapper.selectByHashForUpdate(anyString())).thenReturn(invitation);

        assertThrows(BusinessException.class, () -> service.accept(2L, "expired-token"));

        verify(memberMapper, never()).insert(any(BookMemberEntity.class));
    }

    /** 构建尚未过期的一次性邀请。 */
    private BookInvitationEntity activeInvitation(String role) {
        BookInvitationEntity invitation = new BookInvitationEntity();
        invitation.setId(5L);
        invitation.setBookId(9L);
        invitation.setInviterUserId(1L);
        invitation.setDefaultRole(role);
        invitation.setStatus("ACTIVE");
        invitation.setExpiredTime(LocalDateTime.now(ZoneOffset.UTC).plusHours(1));
        return invitation;
    }

    /** 构建接受邀请的用户资料。 */
    private UserEntity user() {
        UserEntity user = new UserEntity();
        user.setUserNo("U002");
        user.setNickName("朋友");
        return user;
    }
}
