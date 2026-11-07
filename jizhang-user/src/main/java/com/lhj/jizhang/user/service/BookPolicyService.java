package com.lhj.jizhang.user.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.user.dto.BookAccessOutDTO;
import com.lhj.jizhang.user.dto.BookPolicyUpdateInDTO;
import com.lhj.jizhang.user.entity.BookAuditLogEntity;
import com.lhj.jizhang.user.entity.BookMemberEntity;
import com.lhj.jizhang.user.mapper.BookAuditLogMapper;
import com.lhj.jizhang.user.mapper.BookMemberMapper;
import com.lhj.jizhang.user.model.BookPermission;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class BookPolicyService {
    private final BookMemberMapper memberMapper;
    private final BookAuditLogMapper auditMapper;
    private final BookAccessService accessService;
    private final ObjectMapper objectMapper;

    public BookPolicyService(BookMemberMapper memberMapper, BookAuditLogMapper auditMapper,
                             BookAccessService accessService, ObjectMapper objectMapper) {
        this.memberMapper = memberMapper;
        this.auditMapper = auditMapper;
        this.accessService = accessService;
        this.objectMapper = objectMapper;
    }

    /** 返回目标成员当前策略，管理员仅能查看普通成员配置。 */
    public BookAccessOutDTO get(Long actorId, Long bookId, Long memberId) {
        BookMemberEntity actor = accessService.requirePermission(actorId, bookId, BookPermission.MANAGE_MEMBERS);
        BookMemberEntity target = memberMapper.selectById(memberId);
        if (target == null || !Objects.equals(target.getBookId(), bookId) || !Objects.equals(target.getStatus(), 1)
                || "ADMIN".equals(actor.getRole()) && ("ADMIN".equals(target.getRole())
                || "OWNER".equals(target.getRole()))) {
            throw new BusinessException(ErrorCodes.MEMBER_INVALID, "成员不存在或不可查看", HttpStatus.FORBIDDEN);
        }
        return accessService.evaluate(target);
    }

    /** 整体保存经过角色上限校验的策略，并以版本号防止覆盖并发更新。 */
    @Transactional
    public BookAccessOutDTO update(Long actorId, Long bookId, Long memberId, BookPolicyUpdateInDTO input) {
        BookMemberEntity actor = accessService.requirePermission(actorId, bookId, BookPermission.MANAGE_MEMBERS);
        BookMemberEntity target = memberMapper.selectById(memberId);
        validateTarget(actor, target, bookId, input);
        BookMemberEntity candidate = copyCandidate(target, input);
        BookAccessOutDTO effective = accessService.evaluate(candidate);
        if (!effective.actionPermissions().equals(input.actionPermissions())) {
            throw invalidPolicy();
        }
        save(actorId, bookId, memberId, input);
        audit(actorId, bookId, memberId);
        return new BookAccessOutDTO(bookId, input.role(), effective.menuPermissions(),
                effective.actionPermissions(), effective.transactionScope(), input.expectedVersion() + 1);
    }

    /** 限制管理员只管理普通成员且所有人不得修改自己。 */
    private void validateTarget(BookMemberEntity actor, BookMemberEntity target, Long bookId,
                                BookPolicyUpdateInDTO input) {
        if (target == null || !Objects.equals(target.getBookId(), bookId) || !Objects.equals(target.getStatus(), 1)
                || "OWNER".equals(target.getRole())) {
            throw new BusinessException(ErrorCodes.MEMBER_INVALID, "成员不存在或不可修改", HttpStatus.FORBIDDEN);
        }
        if (input.role() == null || !java.util.Set.of("ADMIN", "MEMBER", "VIEWER").contains(input.role())
                || input.expectedVersion() == null || input.expectedVersion() < 0) {
            throw invalidPolicy();
        }
        if (Objects.equals(actor.getId(), target.getId()) || "ADMIN".equals(actor.getRole())
                && ("ADMIN".equals(target.getRole()) || "ADMIN".equals(input.role()))) {
            throw new BusinessException(ErrorCodes.BOOK_ACCESS_DENIED, "无权修改自己或其他管理员的权限",
                    HttpStatus.FORBIDDEN);
        }
    }

    /** 将提交内容构造成尚未持久化的候选策略。 */
    private BookMemberEntity copyCandidate(BookMemberEntity target, BookPolicyUpdateInDTO input) {
        BookMemberEntity candidate = new BookMemberEntity();
        candidate.setBookId(target.getBookId());
        candidate.setRole(input.role());
        candidate.setMenuPermissions(serialize(input.menuPermissions()));
        candidate.setPermissions(serialize(input.actionPermissions()));
        candidate.setTransactionScope(input.transactionScope());
        return candidate;
    }

    /** 使用数据库原子条件更新并将并发失败映射为 409。 */
    private void save(Long actorId, Long bookId, Long memberId, BookPolicyUpdateInDTO input) {
        int updated = memberMapper.updatePolicy(memberId, bookId, input.expectedVersion(), input.role(),
                serialize(input.menuPermissions()), serialize(input.actionPermissions()),
                input.transactionScope(), String.valueOf(actorId));
        if (updated != 1) {
            throw new BusinessException(ErrorCodes.MEMBER_POLICY_CONFLICT, "成员权限已变化，请刷新后重试",
                    HttpStatus.CONFLICT);
        }
    }

    /** 仅记录成员权限变更事件，不记录金额或邀请令牌。 */
    private void audit(Long actorId, Long bookId, Long memberId) {
        BookAuditLogEntity log = new BookAuditLogEntity();
        log.setBookId(bookId);
        log.setOperatorUserId(actorId);
        log.setAction("MEMBER_POLICY_CHANGED");
        log.setObjectType("MEMBER");
        log.setObjectId(memberId);
        log.setSummary("更新成员角色、菜单、操作和账单范围");
        auditMapper.insert(log);
    }

    /** 将已校验的策略值编码为数据库 JSON。 */
    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw invalidPolicy();
        }
    }

    /** 返回客户端可识别的不合法策略异常。 */
    private BusinessException invalidPolicy() {
        return new BusinessException(ErrorCodes.INVALID_PARAMETER, "成员权限配置无效", HttpStatus.BAD_REQUEST);
    }
}
