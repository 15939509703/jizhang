package com.lhj.jizhang.user.service;

import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.user.dto.BookMemberInviteInDTO;
import com.lhj.jizhang.user.dto.BookMemberRoleInDTO;
import com.lhj.jizhang.user.entity.BookMemberEntity;
import com.lhj.jizhang.user.mapper.BookMemberMapper;
import com.lhj.jizhang.user.mapper.UserMapper;
import com.lhj.jizhang.user.model.BookPermission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookMemberServiceTest {
    @Mock private BookMemberMapper memberMapper;
    @Mock private UserMapper userMapper;
    @Mock private BookAccessService accessService;

    private BookMemberService service;

    @BeforeEach
    void setUp() {
        service = new BookMemberService(memberMapper, userMapper, accessService);
    }

    /** 旧按编号直邀不能创建无策略成员。 */
    @Test
    void directInviteRequiresUpgrade() {
        allowManagement();

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.invite(1L, 9L, new BookMemberInviteInDTO("U002", "MEMBER")));

        assertEquals(ErrorCodes.MEMBER_POLICY_UPGRADE_REQUIRED, error.getCode());
        verify(memberMapper, never()).insert(any(BookMemberEntity.class));
    }

    /** 旧角色单字段更新不能覆盖完整策略。 */
    @Test
    void roleOnlyUpdateRequiresUpgrade() {
        allowManagement();

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateRole(1L, 9L, 3L, new BookMemberRoleInDTO("ADMIN")));

        assertEquals(ErrorCodes.MEMBER_POLICY_UPGRADE_REQUIRED, error.getCode());
        verify(memberMapper, never()).updateById(any(BookMemberEntity.class));
    }

    /** 成员移除必须匹配当前策略版本。 */
    @Test
    void removeRejectsConcurrentPolicyChange() {
        allowManagement();
        BookMemberEntity target = new BookMemberEntity();
        target.setId(3L);
        target.setBookId(9L);
        target.setRole("MEMBER");
        target.setStatus(1);
        target.setPolicyVersion(4L);
        when(memberMapper.selectById(3L)).thenReturn(target);

        BusinessException error = assertThrows(BusinessException.class, () -> service.remove(1L, 9L, 3L));

        assertEquals(ErrorCodes.MEMBER_POLICY_CONFLICT, error.getCode());
        verify(memberMapper).deactivate(3L, 9L, 4L, "OWNER", "1");
        verify(memberMapper, never()).updateById(any(BookMemberEntity.class));
    }

    /** 构建可管理普通成员的所有者。 */
    private void allowManagement() {
        BookMemberEntity owner = new BookMemberEntity();
        owner.setRole("OWNER");
        when(accessService.requireAdmin(1L, 9L)).thenReturn(owner);
        when(accessService.requirePermission(1L, 9L, BookPermission.MANAGE_MEMBERS)).thenReturn(owner);
    }
}
