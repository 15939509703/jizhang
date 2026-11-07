package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.user.dto.BookInvitationCreateInDTO;
import com.lhj.jizhang.user.dto.BookAccessOutDTO;
import com.lhj.jizhang.user.entity.BookAuditLogEntity;
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
import com.lhj.jizhang.user.model.BookMenu;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
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
        lenient().when(accessService.evaluate(any(BookMemberEntity.class))).thenAnswer(invocation ->
                new BookAccessService(memberMapper).evaluate(invocation.getArgument(0)));
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

        var result = service.createInvitation(1L, 9L, request("VIEWER"));

        ArgumentCaptor<BookInvitationEntity> saved = ArgumentCaptor.forClass(BookInvitationEntity.class);
        verify(invitationMapper).insert(saved.capture());
        assertEquals("VIEWER", saved.getValue().getDefaultRole());
        assertEquals("ALL", saved.getValue().getTransactionScope());
        assertNotNull(saved.getValue().getActionPermissions());
        assertEquals("VIEWER", result.role());
        assertEquals("ALL", result.access().transactionScope());
        assertNotNull(result.token());
    }

    /** 角色式旧请求不能生成没有策略快照的新邀请。 */
    @Test
    void legacyInvitationRequestRequiresUpgrade() {
        BookMemberEntity owner = new BookMemberEntity();
        owner.setRole("OWNER");
        when(accessService.requirePermission(1L, 9L, BookPermission.MANAGE_MEMBERS)).thenReturn(owner);

        BusinessException error = assertThrows(BusinessException.class, () -> service.createInvitation(1L, 9L,
                new BookInvitationCreateInDTO("MEMBER", 24, null, null, null)));

        assertEquals(ErrorCodes.MEMBER_POLICY_UPGRADE_REQUIRED, error.getCode());
        verify(invitationMapper, never()).insert(any(BookInvitationEntity.class));
    }

    /** 创建、预览均展示邀请创建时的同一有效权限。 */
    @Test
    void previewReturnsFrozenAccess() {
        BookInvitationEntity invitation = activeInvitation("VIEWER");
        when(invitationMapper.selectOne(any(Wrapper.class))).thenReturn(invitation);
        BookEntity book = new BookEntity();
        book.setName("共享账本");
        when(bookMapper.selectById(9L)).thenReturn(book);
        when(userMapper.selectById(1L)).thenReturn(user());

        var result = service.preview(2L, "valid-token");

        assertEquals("VIEWER", result.access().role());
        assertEquals("ALL", result.access().transactionScope());
        assertEquals(request("VIEWER").actionPermissions(), result.access().actionPermissions());
        assertNull(result.token());
    }

    /** 迁移前没有快照的邀请即使仍为 ACTIVE 也不能预览或接受。 */
    @Test
    void legacyInvitationRequiresReissue() {
        BookInvitationEntity invitation = activeInvitation("VIEWER");
        invitation.setMenuPermissions(null);
        when(invitationMapper.selectOne(any(Wrapper.class))).thenReturn(invitation);
        when(invitationMapper.selectByHashForUpdate(anyString())).thenReturn(invitation);

        BusinessException preview = assertThrows(BusinessException.class,
                () -> service.preview(2L, "old-token"));
        BusinessException accept = assertThrows(BusinessException.class,
                () -> service.accept(2L, "old-token"));

        assertEquals(ErrorCodes.MEMBER_INVITATION_REISSUE_REQUIRED, preview.getCode());
        assertEquals(ErrorCodes.MEMBER_INVITATION_REISSUE_REQUIRED, accept.getCode());
        verify(memberMapper, never()).insert(any(BookMemberEntity.class));
    }

    /** 原六项权限写接口不能绕过版本化整体保存。 */
    @Test
    void legacyPermissionsWriteRequiresUpgrade() {
        BookMemberEntity owner = new BookMemberEntity();
        owner.setRole("OWNER");
        when(accessService.requirePermission(1L, 9L, BookPermission.MANAGE_MEMBERS)).thenReturn(owner);

        BusinessException error = assertThrows(BusinessException.class, () -> service.updatePermissions(1L, 9L,
                3L, new com.lhj.jizhang.user.dto.BookMemberPermissionsInDTO(Map.of("CREATE_TRANSACTIONS", true))));

        assertEquals(ErrorCodes.MEMBER_POLICY_UPGRADE_REQUIRED, error.getCode());
        verify(memberMapper, never()).updateById(any(BookMemberEntity.class));
    }

    /** 普通管理员不能通过邀请链接授予其他人管理员权限。 */
    @Test
    void adminCannotCreateAdminInvitation() {
        BookMemberEntity actor = new BookMemberEntity();
        actor.setRole("ADMIN");
        when(accessService.requirePermission(1L, 9L, BookPermission.MANAGE_MEMBERS)).thenReturn(actor);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.createInvitation(1L, 9L, request("ADMIN")));

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
        assertEquals(activeInvitation("VIEWER").getActionPermissions(), saved.getValue().getPermissions());
        assertNotNull(saved.getValue().getMenuPermissions());
        assertEquals("VIEWER", result.role());
    }

    /** 有效成员不能通过已签发邀请重置其现有策略。 */
    @Test
    void existingMemberCannotResetPolicyViaInvitation() {
        BookMemberEntity member = new BookMemberEntity();
        member.setId(3L);
        member.setRole("MEMBER");
        member.setStatus(1);
        member.setPermissions("{\"CREATE_TRANSACTIONS\":true}");
        when(invitationMapper.selectByHashForUpdate(anyString())).thenReturn(activeInvitation("VIEWER"));
        when(memberMapper.selectOne(any(Wrapper.class))).thenReturn(member);
        BusinessException error = assertThrows(BusinessException.class, () -> service.accept(2L, "valid-token"));

        assertEquals(ErrorCodes.MEMBER_INVALID, error.getCode());
        assertEquals("MEMBER", member.getRole());
        verify(memberMapper, never()).updateById(member);
        verify(invitationMapper, never()).updateById(any(BookInvitationEntity.class));
    }

    /** 已停用成员也按新邀请角色恢复，且旧权限覆盖被移除。 */
    @Test
    void inactiveMemberReceivesInvitationRole() {
        BookMemberEntity member = new BookMemberEntity();
        member.setId(3L);
        member.setRole("MEMBER");
        member.setStatus(0);
        member.setBookId(9L);
        member.setPolicyVersion(2L);
        member.setPermissions("{\"EXPORT_DATA\":true}");
        when(invitationMapper.selectByHashForUpdate(anyString())).thenReturn(activeInvitation("VIEWER"));
        when(memberMapper.selectOne(any(Wrapper.class))).thenReturn(member);
        when(userMapper.selectById(2L)).thenReturn(user());
        when(memberMapper.reactivateWithInvitation(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        service.accept(2L, "valid-token");

        assertEquals("VIEWER", member.getRole());
        assertEquals(1, member.getStatus());
        assertEquals(activeInvitation("VIEWER").getActionPermissions(), member.getPermissions());
        verify(memberMapper).reactivateWithInvitation(3L, 9L, 2L, "VIEWER", member.getMenuPermissions(),
                member.getPermissions(), "ALL", 1L, "2");
    }

    /** 停用成员在重入时若策略版本变化，不能消费邀请。 */
    @Test
    void inactiveMemberRejoinConflictDoesNotConsumeInvitation() {
        BookMemberEntity member = new BookMemberEntity();
        member.setId(3L);
        member.setBookId(9L);
        member.setRole("MEMBER");
        member.setStatus(0);
        member.setPolicyVersion(2L);
        when(invitationMapper.selectByHashForUpdate(anyString())).thenReturn(activeInvitation("VIEWER"));
        when(memberMapper.selectOne(any(Wrapper.class))).thenReturn(member);

        BusinessException error = assertThrows(BusinessException.class, () -> service.accept(2L, "valid-token"));

        assertEquals(ErrorCodes.MEMBER_POLICY_CONFLICT, error.getCode());
        verify(invitationMapper, never()).updateById(any(BookInvitationEntity.class));
        assertTrue(member.getStatus() == 0);
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

    /** 操作记录显示操作者昵称和编码，用户资料缺失时不回退为内部 ID。 */
    @Test
    void auditLogsIncludeOperatorProfile() {
        BookAuditLogEntity knownLog = new BookAuditLogEntity();
        knownLog.setId(10L);
        knownLog.setOperatorUserId(2L);
        knownLog.setCreatedTime(LocalDateTime.now(ZoneOffset.UTC));
        BookAuditLogEntity missingLog = new BookAuditLogEntity();
        missingLog.setId(9L);
        missingLog.setOperatorUserId(3L);
        missingLog.setCreatedTime(LocalDateTime.now(ZoneOffset.UTC));
        UserEntity operator = user();
        operator.setId(2L);
        when(auditMapper.selectList(any(Wrapper.class))).thenReturn(List.of(knownLog, missingLog));
        when(userMapper.selectByIds(any())).thenReturn(List.of(operator));

        var logs = service.auditLogs(1L, 9L);

        assertEquals("朋友", logs.get(0).operatorNickName());
        assertEquals("U002", logs.get(0).operatorUserNo());
        assertEquals("未知用户", logs.get(1).operatorNickName());
        assertNull(logs.get(1).operatorUserNo());
    }

    /** 构建尚未过期的一次性邀请。 */
    private BookInvitationEntity activeInvitation(String role) {
        BookInvitationEntity invitation = new BookInvitationEntity();
        invitation.setId(5L);
        invitation.setBookId(9L);
        invitation.setInviterUserId(1L);
        invitation.setDefaultRole(role);
        BookInvitationCreateInDTO policy = request(role);
        invitation.setMenuPermissions(json(policy.menuPermissions()));
        invitation.setActionPermissions(json(policy.actionPermissions()));
        invitation.setTransactionScope(policy.transactionScope());
        invitation.setStatus("ACTIVE");
        invitation.setExpiredTime(LocalDateTime.now(ZoneOffset.UTC).plusHours(1));
        return invitation;
    }

    /** 构建可通过后端求值的完整邀请策略。 */
    private BookInvitationCreateInDTO request(String role) {
        Map<String, Boolean> menus = new LinkedHashMap<>();
        Arrays.stream(BookMenu.values()).forEach(menu -> menus.put(menu.key(), true));
        BookMemberEntity candidate = new BookMemberEntity();
        candidate.setBookId(9L);
        candidate.setRole(role);
        candidate.setMenuPermissions(json(menus));
        candidate.setTransactionScope("ALL");
        BookAccessOutDTO effective = new BookAccessService(memberMapper).evaluate(candidate);
        return new BookInvitationCreateInDTO(role, 24, menus, effective.actionPermissions(), "ALL");
    }

    /** 序列化测试策略。 */
    private String json(Object value) {
        try {
            return new ObjectMapper().writeValueAsString(value);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    /** 构建接受邀请的用户资料。 */
    private UserEntity user() {
        UserEntity user = new UserEntity();
        user.setUserNo("U002");
        user.setNickName("朋友");
        return user;
    }
}
