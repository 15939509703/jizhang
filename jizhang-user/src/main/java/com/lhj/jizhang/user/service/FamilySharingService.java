package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.common.util.BusinessIdGenerator;
import com.lhj.jizhang.user.dto.BookAuditLogOutDTO;
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
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

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
        accessService.requirePermission(userId, bookId, BookPermission.MANAGE_MEMBERS);
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        BookInvitationEntity invitation = new BookInvitationEntity();
        invitation.setInvitationNo(BusinessIdGenerator.next("INV_"));
        invitation.setBookId(bookId);
        invitation.setInviterUserId(userId);
        invitation.setDefaultRole(input.role());
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
        return output(invitation, null);
    }

    @Transactional
    public BookMemberOutDTO accept(Long userId, String token) {
        BookInvitationEntity invitation = invitationMapper.selectByHashForUpdate(hash(token));
        if (invitation == null || !"ACTIVE".equals(invitation.getStatus())
                || invitation.getExpiredTime().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            throw invalidInvitation();
        }
        BookMemberEntity member = memberMapper.selectOne(Wrappers.<BookMemberEntity>lambdaQuery()
                .eq(BookMemberEntity::getBookId, invitation.getBookId()).eq(BookMemberEntity::getUserId, userId));
        if (member == null) {
            member = new BookMemberEntity();
            member.setBookId(invitation.getBookId());
            member.setUserId(userId);
            member.setRole(invitation.getDefaultRole());
            member.setStatus(1);
            member.setInvitedBy(invitation.getInviterUserId());
            member.setJoinedTime(LocalDateTime.now(ZoneOffset.UTC));
            member.setCreator(String.valueOf(userId));
            member.setModifier(String.valueOf(userId));
            memberMapper.insert(member);
        } else if (member.getStatus() == 0) {
            member.setRole(invitation.getDefaultRole());
            member.setStatus(1);
            member.setJoinedTime(LocalDateTime.now(ZoneOffset.UTC));
            member.setModifier(String.valueOf(userId));
            memberMapper.updateById(member);
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
        BookMemberEntity member = memberMapper.selectById(memberId);
        if (member == null || !bookId.equals(member.getBookId()) || member.getStatus() == 0) {
            throw new BusinessException(ErrorCodes.MEMBER_INVALID, "成员不存在");
        }
        if ("OWNER".equals(member.getRole())) {
            throw new BusinessException(ErrorCodes.MEMBER_INVALID, "所有者权限不能覆盖");
        }
        if (!EnumSet.allOf(BookPermission.class).stream().map(Enum::name).toList()
                .containsAll(input.permissions().keySet())) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "包含未知权限");
        }
        try {
            member.setPermissions(objectMapper.writeValueAsString(input.permissions()));
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCodes.INVALID_PARAMETER, "权限配置无效");
        }
        member.setModifier(String.valueOf(userId));
        memberMapper.updateById(member);
        audit(bookId, userId, "MEMBER_PERMISSIONS_CHANGED", "MEMBER", memberId, "修改成员权限");
        UserEntity target = userMapper.selectById(member.getUserId());
        return new BookMemberOutDTO(member.getId(), member.getUserId(), target.getUserNo(), target.getNickName(),
                target.getAvatarUrl(), member.getRole(), member.getStatus());
    }

    public List<BookAuditLogOutDTO> auditLogs(Long userId, Long bookId) {
        accessService.requirePermission(userId, bookId, BookPermission.MANAGE_MEMBERS);
        return auditMapper.selectList(Wrappers.<BookAuditLogEntity>lambdaQuery()
                .eq(BookAuditLogEntity::getBookId, bookId).orderByDesc(BookAuditLogEntity::getId).last("LIMIT 100"))
                .stream().map(item -> new BookAuditLogOutDTO(item.getId(), item.getOperatorUserId(), item.getAction(),
                        item.getObjectType(), item.getObjectId(), item.getSummary(),
                        item.getCreatedTime().toInstant(ZoneOffset.UTC))).toList();
    }

    private BookInvitationOutDTO output(BookInvitationEntity invitation, String token) {
        BookEntity book = bookMapper.selectById(invitation.getBookId());
        UserEntity inviter = userMapper.selectById(invitation.getInviterUserId());
        String status = "ACTIVE".equals(invitation.getStatus())
                && invitation.getExpiredTime().isBefore(LocalDateTime.now(ZoneOffset.UTC)) ? "EXPIRED" : invitation.getStatus();
        return new BookInvitationOutDTO(invitation.getId(), invitation.getInvitationNo(), invitation.getBookId(),
                book.getName(), inviter.getNickName(), invitation.getDefaultRole(), status,
                invitation.getExpiredTime().toInstant(ZoneOffset.UTC), token);
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
