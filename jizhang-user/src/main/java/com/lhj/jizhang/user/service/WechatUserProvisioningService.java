package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.common.util.BusinessIdGenerator;
import com.lhj.jizhang.user.dto.WechatLoginInDTO;
import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.entity.BookMemberEntity;
import com.lhj.jizhang.user.entity.UserAuthEntity;
import com.lhj.jizhang.user.entity.UserEntity;
import com.lhj.jizhang.user.mapper.BookMapper;
import com.lhj.jizhang.user.mapper.BookMemberMapper;
import com.lhj.jizhang.user.mapper.UserAuthMapper;
import com.lhj.jizhang.user.mapper.UserMapper;
import com.lhj.jizhang.user.model.LoginUserContext;
import com.lhj.jizhang.user.model.WechatSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class WechatUserProvisioningService {
    private static final String WECHAT_PROVIDER = "WECHAT";
    private static final String SYSTEM_OPERATOR = "system";

    private final UserMapper userMapper;
    private final UserAuthMapper userAuthMapper;
    private final BookMapper bookMapper;
    private final BookMemberMapper bookMemberMapper;
    private final BookService bookService;
    private final PhoneBindingService phoneBindingService;

    public WechatUserProvisioningService(
            UserMapper userMapper,
            UserAuthMapper userAuthMapper,
            BookMapper bookMapper,
            BookMemberMapper bookMemberMapper,
            BookService bookService,
            PhoneBindingService phoneBindingService
    ) {
        this.userMapper = userMapper;
        this.userAuthMapper = userAuthMapper;
        this.bookMapper = bookMapper;
        this.bookMemberMapper = bookMemberMapper;
        this.bookService = bookService;
        this.phoneBindingService = phoneBindingService;
    }

    /** 按微信身份登录；授权手机号存在时关联到同一手机号的已有账号。 */
    @Transactional
    public LoginUserContext findOrCreate(WechatSession session, WechatLoginInDTO input, String phone) {
        UserAuthEntity auth = findAuth(session.openid());
        if (auth != null) {
            if (phone != null) {
                phoneBindingService.claimVerifiedPhone(auth.getUserId(), phone);
            }
            return loginExistingUser(auth, input);
        }
        Long phoneOwner = phone == null ? null : phoneBindingService.findPhoneOwner(phone);
        if (phoneOwner != null) {
            return linkWechatToPhoneUser(phoneOwner, session, input, phone);
        }
        LoginUserContext context = createNewUser(session, input);
        if (phone != null) {
            phoneBindingService.claimVerifiedPhone(context.user().getId(), phone);
        }
        return context;
    }

    /** 在已有 App 用户上增加微信身份，沿用其用户 ID 和账本。 */
    private LoginUserContext linkWechatToPhoneUser(Long userId, WechatSession session,
                                                    WechatLoginInDTO input, String phone) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCodes.USER_DATA_INVALID, "手机号对应账号状态异常，请联系管理员");
        }
        phoneBindingService.claimVerifiedPhone(userId, phone);
        UserAuthEntity auth = buildAuth(userId, session);
        userAuthMapper.insert(auth);
        updateUserProfile(user, input);
        return new LoginUserContext(user, auth, ensureDefaultBook(user));
    }

    private UserAuthEntity findAuth(String openid) {
        return userAuthMapper.selectOne(Wrappers.<UserAuthEntity>lambdaQuery()
                .eq(UserAuthEntity::getProvider, WECHAT_PROVIDER)
                .eq(UserAuthEntity::getOpenid, openid)
                .eq(UserAuthEntity::getStatus, 1));
    }

    private LoginUserContext loginExistingUser(UserAuthEntity auth, WechatLoginInDTO input) {
        UserEntity user = userMapper.selectById(auth.getUserId());
        if (user == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCodes.USER_DATA_INVALID, "用户状态异常，请联系管理员");
        }
        updateUserProfile(user, input);
        BookEntity defaultBook = ensureDefaultBook(user);
        return new LoginUserContext(user, auth, defaultBook);
    }

    private LoginUserContext createNewUser(WechatSession session, WechatLoginInDTO input) {
        UserEntity user = buildUser(input);
        userMapper.insert(user);
        UserAuthEntity auth = buildAuth(user.getId(), session);
        userAuthMapper.insert(auth);
        BookEntity defaultBook = createDefaultBook(user);
        return new LoginUserContext(user, auth, defaultBook);
    }

    private UserEntity buildUser(WechatLoginInDTO input) {
        UserEntity user = new UserEntity();
        user.setUserNo(BusinessIdGenerator.next("USR_"));
        user.setNickName(valueOrDefault(input.nickName(), "微信用户"));
        user.setAvatarUrl(input.avatarUrl());
        user.setTimezone("Asia/Shanghai");
        user.setStatus(1);
        user.setLastLoginTime(LocalDateTime.now(ZoneOffset.UTC));
        fillOperator(user);
        return user;
    }

    private UserAuthEntity buildAuth(Long userId, WechatSession session) {
        UserAuthEntity auth = new UserAuthEntity();
        auth.setUserId(userId);
        auth.setProvider(WECHAT_PROVIDER);
        auth.setOpenid(session.openid());
        auth.setUnionid(session.unionid());
        auth.setSessionVersion(1);
        auth.setStatus(1);
        auth.setCreator(SYSTEM_OPERATOR);
        auth.setModifier(SYSTEM_OPERATOR);
        return auth;
    }

    private void updateUserProfile(UserEntity user, WechatLoginInDTO input) {
        user.setLastLoginTime(LocalDateTime.now(ZoneOffset.UTC));
        if (!UserAvatarService.hasCustomAvatar(user) && input.avatarUrl() != null && !input.avatarUrl().isBlank()) {
            user.setAvatarUrl(input.avatarUrl());
        }
        user.setModifier(String.valueOf(user.getId()));
        userMapper.updateById(user);
    }

    private BookEntity ensureDefaultBook(UserEntity user) {
        BookMemberEntity membership = bookMemberMapper.selectOne(Wrappers.<BookMemberEntity>lambdaQuery()
                .eq(BookMemberEntity::getUserId, user.getId())
                .eq(BookMemberEntity::getStatus, 1)
                .orderByAsc(BookMemberEntity::getId)
                .last("LIMIT 1"));
        if (membership == null) {
            return createDefaultBook(user);
        }
        BookEntity book = bookMapper.selectById(membership.getBookId());
        if (book == null) {
            throw new BusinessException(ErrorCodes.USER_DATA_INVALID, "默认账本数据异常");
        }
        return book;
    }

    private BookEntity createDefaultBook(UserEntity user) {
        return bookService.createDefault(user.getId());
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void fillOperator(UserEntity user) {
        user.setCreator(SYSTEM_OPERATOR);
        user.setModifier(SYSTEM_OPERATOR);
    }
}
