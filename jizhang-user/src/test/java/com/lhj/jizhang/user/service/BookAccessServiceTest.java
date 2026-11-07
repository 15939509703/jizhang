package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.user.entity.BookMemberEntity;
import com.lhj.jizhang.user.mapper.BookMemberMapper;
import com.lhj.jizhang.user.model.BookMenu;
import com.lhj.jizhang.user.model.BookPermission;
import com.lhj.jizhang.user.model.BookAction;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookAccessServiceTest {
    @Test
    void shouldCreateBeanWithProductionDependencies() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(BookMemberMapper.class, () -> mock(BookMemberMapper.class));
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.register(BookAccessService.class);
            context.refresh();

            assertNotNull(context.getBean(BookAccessService.class));
        }
    }

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

    /** 管理员可以管理成员，但无权执行仅所有者允许的删除操作。 */
    @Test
    void shouldRejectAdminOwnerAccess() {
        BookMemberMapper mapper = mock(BookMemberMapper.class);
        BookMemberEntity member = new BookMemberEntity();
        member.setRole("ADMIN");
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(member);
        BookAccessService service = new BookAccessService(mapper);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.requireOwner(7L, 1L));

        assertEquals(ErrorCodes.BOOK_ACCESS_DENIED, exception.getCode());
    }

    /** 旧邀请和旧直邀创建的无策略成员不能获得默认全账本权限。 */
    @Test
    void memberWithoutPolicyCannotReadBook() {
        BookMemberMapper mapper = mock(BookMemberMapper.class);
        BookMemberEntity member = new BookMemberEntity();
        member.setRole("MEMBER");
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(member);

        assertThrows(BusinessException.class, () -> new BookAccessService(mapper).requireMember(7L, 1L));
    }

    /** 本人账单成员可读账单，但不可访问其他模块或写入。 */
    @Test
    void ownCreatedCanOnlyReadBills() {
        BookMemberMapper mapper = mock(BookMemberMapper.class);
        BookMemberEntity member = configuredMember("MEMBER", "OWN_CREATED");
        member.setMenuPermissions("{\"MENU.BILLS\":true,\"MENU.REPORTS\":false,\"MENU.CONSUMPTION_REPORT\":false,\"MENU.BUDGET\":false,\"MENU.ACCOUNTS\":false,\"MENU.ASSETS\":false,\"MENU.CATEGORIES\":false,\"MENU.RECURRING\":false,\"MENU.LOANS\":false,\"MENU.REIMBURSEMENTS\":false,\"MENU.SAVINGS_GOALS\":false,\"MENU.DATA_SECURITY\":false,\"MENU.FAMILY_SHARING\":false,\"MENU.NOTIFICATIONS\":false}");
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(member);

        BookAccessService service = new BookAccessService(mapper);
        assertEquals("OWN_CREATED", service.requireTransactionScope(7L, 1L).name());
        assertThrows(BusinessException.class, () -> service.requireMenu(7L, 1L, BookMenu.REPORTS));
        assertThrows(BusinessException.class, () -> service.requireWritable(7L, 1L));
    }

    /** 即使角色为 ADMIN，本人账单范围也不能通过仅校验角色的管理入口。 */
    @Test
    void ownCreatedAdminCannotUseRoleOnlyManagement() {
        BookMemberMapper mapper = mock(BookMemberMapper.class);
        BookMemberEntity member = configuredMember("ADMIN", "OWN_CREATED");
        member.setMenuPermissions("{\"MENU.BILLS\":true,\"MENU.REPORTS\":false,\"MENU.CONSUMPTION_REPORT\":false,\"MENU.BUDGET\":false,\"MENU.ACCOUNTS\":false,\"MENU.ASSETS\":false,\"MENU.CATEGORIES\":false,\"MENU.RECURRING\":false,\"MENU.LOANS\":false,\"MENU.REIMBURSEMENTS\":false,\"MENU.SAVINGS_GOALS\":false,\"MENU.DATA_SECURITY\":false,\"MENU.FAMILY_SHARING\":false,\"MENU.NOTIFICATIONS\":false}");
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(member);

        assertThrows(BusinessException.class, () -> new BookAccessService(mapper).requireAdmin(7L, 1L));
    }

    /** 存量成员显式全账本策略保持原角色默认权限。 */
    @Test
    void migratedMemberKeepsOriginalAccess() {
        BookAccessService service = new BookAccessService(mock(BookMemberMapper.class));
        BookMemberEntity member = configuredMember("ADMIN", "ALL");

        assertTrue(service.hasPermission(member, BookPermission.MANAGE_MEMBERS));
        assertTrue(service.evaluate(member).menuPermissions().get(BookMenu.ASSETS.key()));
        assertEquals("ALL", service.evaluate(member).transactionScope());
    }

    /** 创建人范围只允许明细读取，不允许创建或读取其他模块。 */
    @Test
    void ownCreatedIsBillsOnlyAndReadOnly() {
        BookAccessService service = new BookAccessService(mock(BookMemberMapper.class));
        BookMemberEntity member = configuredMember("MEMBER", "OWN_CREATED");
        member.setMenuPermissions("{\"MENU.BILLS\":true,\"MENU.REPORTS\":false,\"MENU.CONSUMPTION_REPORT\":false,\"MENU.BUDGET\":false,\"MENU.ACCOUNTS\":false,\"MENU.ASSETS\":false,\"MENU.CATEGORIES\":false,\"MENU.RECURRING\":false,\"MENU.LOANS\":false,\"MENU.REIMBURSEMENTS\":false,\"MENU.SAVINGS_GOALS\":false,\"MENU.DATA_SECURITY\":false,\"MENU.FAMILY_SHARING\":false,\"MENU.NOTIFICATIONS\":false}");

        assertTrue(service.hasPermission(member, BookPermission.VIEW_TRANSACTIONS));
        assertFalse(service.hasPermission(member, BookPermission.CREATE_TRANSACTIONS));
        assertFalse(service.evaluate(member).menuPermissions().get(BookMenu.ACCOUNTS.key()));
    }

    /** 未知菜单和格式损坏不能回退成管理员全开。 */
    @Test
    void rejectsInvalidMenuPolicy() {
        BookAccessService service = new BookAccessService(mock(BookMemberMapper.class));
        BookMemberEntity member = configuredMember("ADMIN", "ALL");
        member.setMenuPermissions("{\"MENU.UNKNOWN\":true}");

        assertThrows(BusinessException.class, () -> service.evaluate(member));
        member.setMenuPermissions("broken");
        assertThrows(BusinessException.class, () -> service.evaluate(member));
    }

    /** 未知操作和未知范围均不得退回角色权限。 */
    @Test
    void rejectsInvalidActionsAndScope() {
        BookAccessService service = new BookAccessService(mock(BookMemberMapper.class));
        BookMemberEntity member = configuredMember("ADMIN", "ALL");
        member.setPermissions("{\"UNKNOWN\":true}");
        assertThrows(BusinessException.class, () -> service.evaluate(member));
        member.setPermissions(null);
        member.setTransactionScope("UNKNOWN");
        assertThrows(BusinessException.class, () -> service.evaluate(member));
        member.setTransactionScope("ALL");
        member.setRole("UNKNOWN");
        assertThrows(BusinessException.class, () -> service.evaluate(member));
    }

    /** 多段模块名的动作键仍保持模块与按钮两级结构。 */
    @Test
    void actionKeysPreserveMultiwordMenuNames() {
        assertEquals("ACTION.DATA_SECURITY.BACKUP", BookAction.DATA_SECURITY_BACKUP.key());
        assertEquals("ACTION.SAVINGS_GOALS.CONTRIBUTE", BookAction.SAVINGS_GOALS_CONTRIBUTE.key());
    }

    /** 老成员禁用创建权限时，新动作的缺省值不得意外重新开放写入。 */
    @Test
    void legacyWriteOverrideAlsoLimitsNewActionDefaults() {
        BookAccessService service = new BookAccessService(mock(BookMemberMapper.class));
        BookMemberEntity member = configuredMember("MEMBER", "ALL");
        member.setPermissions("{\"CREATE_TRANSACTIONS\":false}");

        assertFalse(service.evaluate(member).actionPermissions().get(BookAction.ACCOUNTS_MANAGE.key()));
        assertTrue(service.evaluate(member).actionPermissions().get(BookAction.BILLS_SEARCH.key()));
    }

    /** 禁用菜单和业务动作时均由服务端拒绝直接调用。 */
    @Test
    void disabledMenuAndActionRejectDirectRequests() {
        BookMemberMapper mapper = mock(BookMemberMapper.class);
        BookMemberEntity member = configuredMember("ADMIN", "ALL");
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(member);
        BookAccessService service = new BookAccessService(mapper);
        member.setPermissions("{\"ACTION.BILLS.VOID\":false}");
        assertThrows(BusinessException.class, () -> service.requireAction(7L, 1L, BookAction.BILLS_VOID));
        member.setMenuPermissions(member.getMenuPermissions().replace("\"MENU.ACCOUNTS\":true",
                "\"MENU.ACCOUNTS\":false"));
        assertThrows(BusinessException.class, () -> service.requireMenu(7L, 1L, BookMenu.ACCOUNTS));
        assertThrows(BusinessException.class, () -> service.requireLedgerFileAccess(7L, 1L));
    }

    /** 构造已完成兼容回填的成员策略。 */
    private BookMemberEntity configuredMember(String role, String scope) {
        BookMemberEntity member = new BookMemberEntity();
        member.setRole(role);
        member.setTransactionScope(scope);
        member.setMenuPermissions("{\"MENU.BILLS\":true,\"MENU.REPORTS\":true,\"MENU.CONSUMPTION_REPORT\":true,\"MENU.BUDGET\":true,\"MENU.ACCOUNTS\":true,\"MENU.ASSETS\":true,\"MENU.CATEGORIES\":true,\"MENU.RECURRING\":true,\"MENU.LOANS\":true,\"MENU.REIMBURSEMENTS\":true,\"MENU.SAVINGS_GOALS\":true,\"MENU.DATA_SECURITY\":true,\"MENU.FAMILY_SHARING\":true,\"MENU.NOTIFICATIONS\":true}");
        return member;
    }
}
