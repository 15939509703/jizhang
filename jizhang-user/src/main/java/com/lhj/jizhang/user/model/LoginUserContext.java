package com.lhj.jizhang.user.model;

import com.lhj.jizhang.user.entity.BookEntity;
import com.lhj.jizhang.user.entity.UserAuthEntity;
import com.lhj.jizhang.user.entity.UserEntity;

public record LoginUserContext(
        UserEntity user,
        UserAuthEntity auth,
        BookEntity defaultBook
) {
}
