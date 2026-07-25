package com.lhj.jizhang.user.service;

import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.common.util.BusinessIdGenerator;
import com.lhj.jizhang.security.model.TokenPair;
import com.lhj.jizhang.security.service.TokenService;
import com.lhj.jizhang.user.dto.LoginBookOutDTO;
import com.lhj.jizhang.user.dto.LoginUserOutDTO;
import com.lhj.jizhang.user.dto.PasswordCredentialInDTO;
import com.lhj.jizhang.user.dto.WechatLoginOutDTO;
import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.entity.UserAuthEntity;
import com.lhj.jizhang.user.entity.UserEntity;
import com.lhj.jizhang.user.mapper.UserAuthMapper;
import com.lhj.jizhang.user.mapper.UserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class PhoneAccountProvisioningService {
    private static final String PHONE_PROVIDER = "PHONE";
    private static final String SYSTEM_OPERATOR = "system";

    private final UserMapper userMapper;
    private final UserAuthMapper userAuthMapper;
    private final PasswordCredentialService credentialService;
    private final BookService bookService;
    private final TokenService tokenService;

    public PhoneAccountProvisioningService(
            UserMapper userMapper,
            UserAuthMapper userAuthMapper,
            PasswordCredentialService credentialService,
            BookService bookService,
            TokenService tokenService
    ) {
        this.userMapper = userMapper;
        this.userAuthMapper = userAuthMapper;
        this.credentialService = credentialService;
        this.bookService = bookService;
        this.tokenService = tokenService;
    }

    @Transactional
    public WechatLoginOutDTO create(String phone, String password) {
        UserEntity user;
        UserAuthEntity auth;
        try {
            user = createUser(phone);
            auth = createAuth(user.getId(), phone);
            credentialService.bind(user.getId(), new PasswordCredentialInDTO(phone, password));
        } catch (DuplicateKeyException exception) {
            throw phoneAlreadyRegistered();
        }
        BookEntity book = bookService.createDefault(user.getId());
        TokenPair tokens = tokenService.issue(user.getId(), auth.getSessionVersion());
        return toOutput(user, book, tokens);
    }

    private UserEntity createUser(String phone) {
        UserEntity user = new UserEntity();
        user.setUserNo(BusinessIdGenerator.next("USR_"));
        user.setNickName("用户" + phone.substring(7));
        user.setTimezone("Asia/Shanghai");
        user.setStatus(1);
        user.setLastLoginTime(LocalDateTime.now(ZoneOffset.UTC));
        user.setCreator(SYSTEM_OPERATOR);
        user.setModifier(SYSTEM_OPERATOR);
        userMapper.insert(user);
        return user;
    }

    private UserAuthEntity createAuth(Long userId, String phone) {
        UserAuthEntity auth = new UserAuthEntity();
        auth.setUserId(userId);
        auth.setProvider(PHONE_PROVIDER);
        auth.setOpenid(phone);
        auth.setSessionVersion(1);
        auth.setStatus(1);
        auth.setCreator(String.valueOf(userId));
        auth.setModifier(String.valueOf(userId));
        userAuthMapper.insert(auth);
        return auth;
    }

    private WechatLoginOutDTO toOutput(UserEntity user, BookEntity book, TokenPair tokens) {
        return new WechatLoginOutDTO(
                tokens.accessToken(),
                tokens.refreshToken(),
                tokens.expiresIn(),
                new LoginUserOutDTO(user.getId(), user.getUserNo(), user.getNickName(), user.getAvatarUrl()),
                new LoginBookOutDTO(book.getId(), book.getBookNo(), book.getName())
        );
    }

    private BusinessException phoneAlreadyRegistered() {
        return new BusinessException(
                ErrorCodes.PHONE_ALREADY_REGISTERED,
                "手机号已注册，请直接登录",
                HttpStatus.CONFLICT
        );
    }
}
