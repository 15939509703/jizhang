package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.common.util.BusinessIdGenerator;
import com.lhj.jizhang.user.dto.BookAuditLogOutDTO;
import com.lhj.jizhang.user.dto.BookAccessOutDTO;
import com.lhj.jizhang.user.dto.BookInvitationCreateInDTO;
import com.lhj.jizhang.user.dto.BookInvitationOutDTO;
import com.lhj.jizhang.user.dto.BookMemberOutDTO;
import com.lhj.jizhang.user.dto.BookMemberPermissionsInDTO;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FamilySharingService {
    private final BookInvitationMapper invitationMapper;
    private final BookMemberMapper memberMapper;
    private final BookAuditLogMapper auditMapper;
    private final BookMapper bookMapper;
    private final UserMapper userMapper;
    private final BookAccessService accessService;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public FamilySharingService(BookInvitationMapper invitationMapper, BookMemberMapper memberMapper,
                                BookAuditLogMapper auditMapper, BookMapper bookMapper, UserMapper userMapper,
                                BookAccessService accessService, ObjectMapper objectMapper) {
        this.invitationMapper = invitationMapper;
        this.memberMapper = memberMapper;
        this.auditMapper = auditMapper;
        this.bookMapper = bookMapper;
        this.userMapper = userMapper;
        this.accessService = accessService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public BookInvitationOutDTO createInvitation(Long userId, Long bookId, BookInvitationCreateInDTO input) {
        BookMemberEntity actor = accessService.requirePermission(userId, bookId, BookPermission.MANAGE_MEMBERS);
        if ("ADMIN".equals(input.role()) && !"OWNER".equals(actor.getRole())) {
            throw new BusinessException(ErrorCodes.BOOK_ACCESS_DENIED, "仅账本所有者可邀请管理员", HttpStatus.FORBIDDEN);
        }
        BookAccessOutDTO effective = validateInvitationPolicy(bookId, input);
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        BookInvitationEntity invitation = new BookInvitationEntity();
        invitation.setInvitationNo(BusinessIdGenerator.next("INV_"));
        invitation.setBookId(bookId);
        invitation.setInviterUserId(userId);
        invitation.setDefaultRole(input.role());
        invitation.setMenuPermissions(serialize(effective.menuPermissions()));
        invitation.setActionPermissions(serialize(effective.actionPermissions()));
        invitation.setTransactionScope(effective.transactionScope());
        invitation.setTokenHash(hash(token));
        invitation.setExpiredTime(LocalDateTime.now(ZoneOffset.UTC).plusHours(input.expiresInHours()));
        invitation.setStatus("ACTIVE");
        invitation.setCreator(String.valueOf(userId));
        invitation.setModifier(String.valueOf(userId));
        invitationMapper.insert(invitation);
        audit(bookId, userId, "INVITATION_CREATED", "INVITATION", invitation.getId(), "创建一次性邀请");
        return output(invitation, token);
    }

    public BookInvitationOutDTO preview(Long userId, String token) {
        BookInvitationEntity invitation = invitationMapper.selectOne(Wrappers.<BookInvitationEntity>lambdaQuery()
                .eq(BookInvitationEntity::getTokenHash, hash(token)));
        if (invitation == null) throw invalidInvitation();
        requireSnapshot(invitation);
        return output(invitation, null);
    }

    @Transactional
    public BookMemberOutDTO accept(Long userId, String token) {
        BookInvitationEntity invitation = invitationMapper.selectByHashForUpdate(hash(token));
        if (invitation != null) {
            requireSnapshot(invitation);
            snapshotAccess(invitation);
        }
        if (invitation == null || !"ACTIVE".equals(invitation.getStatus())
                || invitation.getExpiredTime().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            throw invalidInvitation();
        }
        BookMemberEntity member = memberMapper.selectOne(Wrappers.<BookMemberEntity>lambdaQuery()
                .eq(BookMemberEntity::getBookId, invitation.getBookId()).eq(BookMemberEntity::getUserId, userId));
        if (member != null && member.getStatus() == 1) {
            throw new BusinessException(ErrorCodes.MEMBER_INVALID, "已是账本成员，不能通过邀请重置权限", HttpStatus.CONFLICT);
        }
        if (member == null) {
            member = new BookMemberEntity();
            member.setBookId(invitation.getBookId());
            member.setUserId(userId);
            copySnapshot(member, invitation);
            member.setPolicyVersion(0L);
            member.setStatus(1);
            member.setInvitedBy(invitation.getInviterUserId());
            member.setJoinedTime(LocalDateTime.now(ZoneOffset.UTC));
            member.setCreator(String.valueOf(userId));
            member.setModifier(String.valueOf(userId));
            memberMapper.insert(member);
        } else {
            if (memberMapper.reactivateWithInvitation(member.getId(), invitation.getBookId(),
                    member.getPolicyVersion(), invitation.getDefaultRole(), invitation.getMenuPermissions(),
                    invitation.getActionPermissions(), invitation.getTransactionScope(), invitation.getInviterUserId(),
                    String.valueOf(userId)) != 1) {
                throw new BusinessException(ErrorCodes.MEMBER_POLICY_CONFLICT, "成员状态已变化，请刷新后重试",
                        HttpStatus.CONFLICT);
            }
            copySnapshot(member, invitation);
            member.setStatus(1);
        }
        invitation.setStatus("ACCEPTED");
        invitation.setAcceptedUserId(userId);
        invitation.setAcceptedTime(LocalDateTime.now(ZoneOffset.UTC));
        invitation.setModifier(String.valueOf(userId));
        invitationMapper.updateById(invitation);
        audit(invitation.getBookId(), userId, "MEMBER_JOINED", "MEMBER", member.getId(), "通过邀请加入账本");
        UserEntity user = userMapper.selectById(userId);
        return new BookMemberOutDTO(member.getId(), userId, user.getUserNo(), user.getNickName(), user.getAvatarUrl(),
                member.getRole(), member.getStatus());
    }

    @Transactional
    public void revoke(Long userId, Long bookId, Long invitationId) {
        accessService.requirePermission(userId, bookId, BookPermission.MANAGE_MEMBERS);
        BookInvitationEntity invitation = invitationMapper.selectById(invitationId);
        if (invitation == null || !bookId.equals(invitation.getBookId())) throw invalidInvitation();
        if ("ACTIVE".equals(invitation.getStatus())) {
            invitation.setStatus("REVOKED");
            invitation.setModifier(String.valueOf(userId));
            invitationMapper.updateById(invitation);
            audit(bookId, userId, "INVITATION_REVOKED", "INVITATION", invitationId, "撤销邀请");
        }
    }

    @Transactional
    public BookMemberOutDTO updatePermissions(Long userId, Long bookId, Long memberId,
                                              BookMemberPermissionsInDTO input) {
        accessService.requirePermission(userId, bookId, BookPermission.MANAGE_MEMBERS);
        throw upgradeRequired();
    }

    public List<BookAuditLogOutDTO> auditLogs(Long userId, Long bookId) {
        accessService.requirePermission(userId, bookId, BookPermission.MANAGE_MEMBERS);
        List<BookAuditLogEntity> logs = auditMapper.selectList(Wrappers.<BookAuditLogEntity>lambdaQuery()
                .eq(BookAuditLogEntity::getBookId, bookId).orderByDesc(BookAuditLogEntity::getId).last("LIMIT 100"));
        if (logs.isEmpty()) return List.of();
        Map<Long, UserEntity> users = userMapper.selectByIds(logs.stream()
                        .map(BookAuditLogEntity::getOperatorUserId).distinct().toList()).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
        return logs.stream().map(item -> {
            UserEntity operator = users.get(item.getOperatorUserId());
            return new BookAuditLogOutDTO(item.getId(), item.getOperatorUserId(), item.getAction(),
                    item.getObjectType(), item.getObjectId(), item.getSummary(),
                    item.getCreatedTime().toInstant(ZoneOffset.UTC),
                    operator == null ? "未知用户" : operator.getNickName(),
                    operator == null ? null : operator.getUserNo());
        }).toList();
    }

    private BookInvitationOutDTO output(BookInvitationEntity invitation, String token) {
        BookEntity book = bookMapper.selectById(invitation.getBookId());
        UserEntity inviter = userMapper.selectById(invitation.getInviterUserId());
        String status = "ACTIVE".equals(invitation.getStatus())
                && invitation.getExpiredTime().isBefore(LocalDateTime.now(ZoneOffset.UTC)) ? "EXPIRED" : invitation.getStatus();
        return new BookInvitationOutDTO(invitation.getId(), invitation.getInvitationNo(), invitation.getBookId(),
                book.getName(), inviter.getNickName(), invitation.getDefaultRole(), status,
                invitation.getExpiredTime().toInstant(ZoneOffset.UTC), token, snapshotAccess(invitation));
    }

    /** 邀请创建时校验完整策略及角色上限，不为缺失字段填默认全开。 */
    private BookAccessOutDTO validateInvitationPolicy(Long bookId, BookInvitationCreateInDTO input) {
        if (input.menuPermissions() == null || input.actionPermissions() == null
                || input.transactionScope() == null) {
            throw upgradeRequired();
        }
        BookMemberEntity candidate = new BookMemberEntity();
        candidate.setBookId(bookId);
        candidate.setRole(input.role());
        candidate.setMenuPermissions(serialize(input.menuPermissions()));
        candidate.setPermissions(serialize(input.actionPermissions()));
        candidate.setTransactionScope(input.transactionScope());
        BookAccessOutDTO effective = accessService.evaluate(candidate);
        if (!effective.actionPermissions().equals(input.actionPermissions())) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "邀请权限配置无效");
        }
        return effective;
    }

    /** 无策略快照的历史邀请不得预览或接受，必须重新发起。 */
    private void requireSnapshot(BookInvitationEntity invitation) {
        if (invitation.getMenuPermissions() == null || invitation.getActionPermissions() == null
                || invitation.getTransactionScope() == null) {
            throw new BusinessException(ErrorCodes.MEMBER_INVITATION_REISSUE_REQUIRED, "旧邀请已失效，请重新邀请",
                    HttpStatus.CONFLICT);
        }
    }

    /** 从已冻结的邀请构造有效权限摘要，非法快照拒绝访问。 */
    private BookAccessOutDTO snapshotAccess(BookInvitationEntity invitation) {
        BookMemberEntity candidate = new BookMemberEntity();
        candidate.setBookId(invitation.getBookId());
        candidate.setRole(invitation.getDefaultRole());
        candidate.setMenuPermissions(invitation.getMenuPermissions());
        candidate.setPermissions(invitation.getActionPermissions());
        candidate.setTransactionScope(invitation.getTransactionScope());
        BookAccessOutDTO effective = accessService.evaluate(candidate);
        try {
            Map<String, Boolean> actions = objectMapper.readValue(invitation.getActionPermissions(),
                    new TypeReference<>() {});
            if (!effective.actionPermissions().equals(actions)) {
                throw new BusinessException(ErrorCodes.MEMBER_INVALID, "邀请权限配置无效");
            }
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCodes.MEMBER_INVALID, "邀请权限配置无效");
        }
        return effective;
    }

    /** 将快照原样复制到成员策略，不按角色重新计算默认权限。 */
    private void copySnapshot(BookMemberEntity member, BookInvitationEntity invitation) {
        member.setRole(invitation.getDefaultRole());
        member.setMenuPermissions(invitation.getMenuPermissions());
        member.setPermissions(invitation.getActionPermissions());
        member.setTransactionScope(invitation.getTransactionScope());
    }

    /** 编码已校验的菜单和动作配置。 */
    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "邀请权限配置无效");
        }
    }

    /** 要求旧客户端升级为完整策略写接口。 */
    private BusinessException upgradeRequired() {
        return new BusinessException(ErrorCodes.MEMBER_POLICY_UPGRADE_REQUIRED, "请升级客户端并提交完整权限配置",
                HttpStatus.CONFLICT);
    }

    private void audit(Long bookId, Long userId, String action, String type, Long objectId, String summary) {
        BookAuditLogEntity log = new BookAuditLogEntity();
        log.setBookId(bookId); log.setOperatorUserId(userId); log.setAction(action); log.setObjectType(type);
        log.setObjectId(objectId); log.setSummary(summary); auditMapper.insert(log);
    }

    private String hash(String token) {
        if (token == null || token.isBlank() || token.length() > 128) throw invalidInvitation();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new BusinessException(ErrorCodes.INTERNAL_ERROR, "邀请服务不可用", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private BusinessException invalidInvitation() {
        return new BusinessException(ErrorCodes.MEMBER_INVALID, "邀请不存在、已使用或已过期");
    }
}
