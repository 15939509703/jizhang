package com.lhj.jizhang.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhj.jizhang.common.exception.BusinessException;
import com.lhj.jizhang.common.exception.ErrorCodes;
import com.lhj.jizhang.security.model.TokenPair;
import com.lhj.jizhang.security.service.TokenService;
import com.lhj.jizhang.user.dto.LoginBookOutDTO;
import com.lhj.jizhang.user.dto.LoginUserOutDTO;
import com.lhj.jizhang.user.dto.PasswordLoginInDTO;
import com.lhj.jizhang.user.dto.WechatLoginOutDTO;
import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.entity.BookMemberEntity;
import com.lhj.jizhang.user.entity.UserAuthEntity;
import com.lhj.jizhang.user.entity.UserCredentialEntity;
import com.lhj.jizhang.user.entity.UserEntity;
import com.lhj.jizhang.user.mapper.BookMapper;
import com.lhj.jizhang.user.mapper.BookMemberMapper;
import com.lhj.jizhang.user.mapper.UserAuthMapper;
import com.lhj.jizhang.user.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PasswordLoginService {
    private final PasswordCredentialService credentialService;
    private final UserMapper userMapper;
    private final UserAuthMapper userAuthMapper;
    private final BookMapper bookMapper;
    private final BookMemberMapper bookMemberMapper;
    private final BookService bookService;
    private final TokenService tokenService;

    public PasswordLoginService(
            PasswordCredentialService credentialService,
            UserMapper userMapper,
            UserAuthMapper userAuthMapper,
            BookMapper bookMapper,
            BookMemberMapper bookMemberMapper,
            BookService bookService,
            TokenService tokenService
    ) {
        this.credentialService = credentialService;
        this.userMapper = userMapper;
        this.userAuthMapper = userAuthMapper;
        this.bookMapper = bookMapper;
        this.bookMemberMapper = bookMemberMapper;
        this.bookService = bookService;
        this.tokenService = tokenService;
    }

    public WechatLoginOutDTO login(PasswordLoginInDTO input) {
        UserCredentialEntity credential = credentialService.findByUsername(input.username());
        if (credential != null && credentialService.isLocked(credential)) {
            throw new BusinessException(ErrorCodes.PASSWORD_CREDENTIAL_LOCKED, "登录尝试过多，请稍后再试", HttpStatus.TOO_MANY_REQUESTS);
        }
        if (credential == null || !credentialService.matches(input.password(), credential.getPasswordHash())) {
            if (credential != null) {
                credentialService.recordFailure(credential);
            }
            throw invalidLogin();
        }
        UserEntity user = requireActiveUser(credential.getUserId());
        BookEntity book = ensureDefaultBook(user.getId());
        credentialService.recordSuccess(credential);
        TokenPair tokens = tokenService.issue(user.getId(), sessionVersion(user.getId()));
        return toOutput(user, book, tokens);
    }

    private UserEntity requireActiveUser(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCodes.USER_DATA_INVALID, "用户状态异常，请重新登录");
        }
        return user;
    }

    private BookEntity ensureDefaultBook(Long userId) {
        BookMemberEntity membership = bookMemberMapper.selectOne(Wrappers.<BookMemberEntity>lambdaQuery()
                .eq(BookMemberEntity::getUserId, userId)
                .eq(BookMemberEntity::getStatus, 1)
                .orderByAsc(BookMemberEntity::getId)
                .last("LIMIT 1"));
        if (membership == null) {
            return bookService.createDefault(userId);
        }
        BookEntity book = bookMapper.selectById(membership.getBookId());
        if (book == null || book.getStatus() == null || book.getStatus() == 0) {
            throw new BusinessException(ErrorCodes.USER_DATA_INVALID, "默认账本数据异常");
        }
        return book;
    }

    private int sessionVersion(Long userId) {
        UserAuthEntity auth = userAuthMapper.selectOne(Wrappers.<UserAuthEntity>lambdaQuery()
                .eq(UserAuthEntity::getUserId, userId)
                .eq(UserAuthEntity::getStatus, 1)
                .orderByAsc(UserAuthEntity::getId)
                .last("LIMIT 1"));
        return auth == null || auth.getSessionVersion() == null ? 1 : auth.getSessionVersion();
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

    private BusinessException invalidLogin() {
        return new BusinessException(ErrorCodes.PASSWORD_LOGIN_FAILED, "账号或密码错误", HttpStatus.UNAUTHORIZED);
    }
}
