package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.user.dto.BookAccessOutDTO;
import com.lhj.jizhang.user.dto.BookPolicyUpdateInDTO;
import com.lhj.jizhang.user.entity.BookAuditLogEntity;
import com.lhj.jizhang.user.entity.BookMemberEntity;
import com.lhj.jizhang.user.mapper.BookAuditLogMapper;
import com.lhj.jizhang.user.mapper.BookMemberMapper;
import com.lhj.jizhang.user.model.BookMenu;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookPolicyServiceTest {
    private final BookMemberMapper memberMapper = mock(BookMemberMapper.class);
    private final BookAuditLogMapper auditMapper = mock(BookAuditLogMapper.class);
    private final BookAccessService accessService = new BookAccessService(memberMapper);
    private final BookPolicyService service = new BookPolicyService(memberMapper, auditMapper,
            accessService, new ObjectMapper());

    /** 所有者可整体保存普通成员的全账本策略并写入不含金额的审计。 */
    @Test
    void ownerCanSaveAllScope() {
        when(memberMapper.selectOne(any(Wrapper.class))).thenReturn(actor("OWNER", 1L));
        when(memberMapper.selectById(3L)).thenReturn(target("MEMBER", 3L));
        when(memberMapper.updatePolicy(eq(3L), eq(9L), eq(0L), eq("MEMBER"), any(), any(),
                eq("ALL"), eq("1"))).thenReturn(1);

        BookAccessOutDTO result = service.update(1L, 9L, 3L, request("MEMBER"));

        assertEquals("ALL", result.transactionScope());
        assertEquals(1L, result.policyVersion());
        verify(auditMapper).insert(any(BookAuditLogEntity.class));
    }

    /** 版本冲突不能写入权限变更审计。 */
    @Test
    void conflictDoesNotAudit() {
        when(memberMapper.selectOne(any(Wrapper.class))).thenReturn(actor("OWNER", 1L));
        when(memberMapper.selectById(3L)).thenReturn(target("MEMBER", 3L));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.update(1L, 9L, 3L, request("MEMBER")));

        assertEquals(ErrorCodes.MEMBER_POLICY_CONFLICT, error.getCode());
        verify(auditMapper, never()).insert(any(BookAuditLogEntity.class));
    }

    /** 管理员不能修改其他管理员，避免通过新接口绕过角色上限。 */
    @Test
    void adminCannotManageAdmin() {
        when(memberMapper.selectOne(any(Wrapper.class))).thenReturn(actor("ADMIN", 1L));
        when(memberMapper.selectById(3L)).thenReturn(target("ADMIN", 3L));

        assertThrows(BusinessException.class, () -> service.update(1L, 9L, 3L, request("MEMBER")));
        verify(memberMapper, never()).updatePolicy(any(), any(), any(), any(), any(), any(), any(), any());
    }

    /** 管理员可授予普通成员全账本范围，但不能修改自己的策略。 */
    @Test
    void adminCanGrantAllToOrdinaryMemberButNotSelf() {
        when(memberMapper.selectOne(any(Wrapper.class))).thenReturn(actor("ADMIN", 1L));
        when(memberMapper.selectById(3L)).thenReturn(target("MEMBER", 3L));
        when(memberMapper.selectById(1L)).thenReturn(target("ADMIN", 1L));
        when(memberMapper.updatePolicy(eq(3L), eq(9L), eq(0L), eq("MEMBER"), any(), any(),
                eq("ALL"), eq("1"))).thenReturn(1);

        assertEquals("ALL", service.update(1L, 9L, 3L, request("MEMBER")).transactionScope());
        assertThrows(BusinessException.class, () -> service.update(1L, 9L, 1L, request("MEMBER")));
    }

    /** 管理员可以读取普通成员的策略，但不能读取其他管理员配置。 */
    @Test
    void adminReadsOnlyManageableMemberAccess() {
        when(memberMapper.selectOne(any(Wrapper.class))).thenReturn(actor("ADMIN", 1L));
        when(memberMapper.selectById(3L)).thenReturn(target("MEMBER", 3L));
        when(memberMapper.selectById(4L)).thenReturn(target("ADMIN", 4L));

        assertEquals("ALL", service.get(1L, 9L, 3L).transactionScope());
        assertThrows(BusinessException.class, () -> service.get(1L, 9L, 4L));
    }

    /** 构造角色、菜单和动作完整且相互一致的保存请求。 */
    private BookPolicyUpdateInDTO request(String role) {
        Map<String, Boolean> menus = new LinkedHashMap<>();
        Arrays.stream(BookMenu.values()).forEach(menu -> menus.put(menu.key(), true));
        BookMemberEntity candidate = target(role, 3L);
        candidate.setMenuPermissions(toJson(menus));
        candidate.setTransactionScope("ALL");
        return new BookPolicyUpdateInDTO(0L, role, menus,
                accessService.evaluate(candidate).actionPermissions(), "ALL");
    }

    /** 构造可执行成员管理操作的现有角色。 */
    private BookMemberEntity actor(String role, Long id) {
        BookMemberEntity member = target(role, id);
        member.setStatus(1);
        return member;
    }

    /** 构造未修改的账本成员。 */
    private BookMemberEntity target(String role, Long id) {
        BookMemberEntity member = new BookMemberEntity();
        member.setId(id);
        member.setBookId(9L);
        member.setRole(role);
        member.setStatus(1);
        member.setTransactionScope("ALL");
        Map<String, Boolean> menus = new LinkedHashMap<>();
        Arrays.stream(BookMenu.values()).forEach(menu -> menus.put(menu.key(), true));
        member.setMenuPermissions(toJson(menus));
        return member;
    }

    /** 使用与生产一致的 JSON 编码构造测试策略。 */
    private String toJson(Object value) {
        try {
            return new ObjectMapper().writeValueAsString(value);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
