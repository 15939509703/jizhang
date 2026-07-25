package com.lhj.jizhang.common.exception;

public final class ErrorCodes {
    public static final String INVALID_PARAMETER = "COMMON_INVALID_PARAMETER";
    public static final String INTERNAL_ERROR = "COMMON_INTERNAL_ERROR";
    public static final String UNAUTHORIZED = "AUTH_UNAUTHORIZED";
    public static final String WECHAT_LOGIN_FAILED = "AUTH_WECHAT_LOGIN_FAILED";
    public static final String PASSWORD_LOGIN_FAILED = "AUTH_PASSWORD_LOGIN_FAILED";
    public static final String PASSWORD_CREDENTIAL_EXISTS = "AUTH_PASSWORD_CREDENTIAL_EXISTS";
    public static final String PASSWORD_CREDENTIAL_LOCKED = "AUTH_PASSWORD_CREDENTIAL_LOCKED";
    public static final String USER_DATA_INVALID = "AUTH_USER_DATA_INVALID";
    public static final String BOOK_ACCESS_DENIED = "BOOK_ACCESS_DENIED";
    public static final String BOOK_NOT_FOUND = "BOOK_NOT_FOUND";
    public static final String ACCOUNT_INVALID = "ACCOUNT_INVALID";
    public static final String CATEGORY_INVALID = "CATEGORY_INVALID";
    public static final String TRANSACTION_INVALID = "TRANSACTION_INVALID";
    public static final String TRANSACTION_CONFLICT = "TRANSACTION_CONFLICT";
    public static final String MEMBER_INVALID = "MEMBER_INVALID";
    public static final String ATTACHMENT_INVALID = "ATTACHMENT_INVALID";

    private ErrorCodes() {
    }
}
