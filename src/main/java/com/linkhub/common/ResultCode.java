package com.linkhub.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResultCode {

    // --- 1. 基础通用状态码 ---
    SUCCESS(200, MessageConstants.OPERATION_SUCCESS),
    BAD_REQUEST(400, MessageConstants.PARAM_ERROR),
    UNAUTHORIZED(401, MessageConstants.HTTP_401),
    FORBIDDEN(403, MessageConstants.HTTP_403),
    NOT_FOUND(404, MessageConstants.HTTP_404),
    TOO_MANY_REQUESTS(429, MessageConstants.TOO_MANY_REQUESTS),
    INTERNAL_SERVER_ERROR(500, MessageConstants.SYSTEM_BUSY),

    // --- 2. 用户与认证业务线 ---
    USER_NOT_EXIST(10001, MessageConstants.USER_NOT_EXIST),
    USER_PASSWORD_ERROR(10002, MessageConstants.USER_PASSWORD_ERROR),
    USER_ACCOUNT_LOCKED(10003, MessageConstants.USER_ACCOUNT_LOCKED),
    USER_EMAIL_REGISTERED(10004, MessageConstants.USER_EMAIL_REGISTERED),
    USER_UNREGISTERED(10005, MessageConstants.USER_UNREGISTERED),
    TOKEN_EXPIRED(10006, "登录已过期，请刷新令牌"),
    TOKEN_INVALID(10007, "令牌无效"),
    // --- 3. 验证码业务线 ---
    VERIFY_CODE_ERROR(20001, MessageConstants.VERIFY_CODE_ERROR),
    VERIFY_CODE_SEND_FREQUENTLY(20002, MessageConstants.VERIFY_CODE_SEND_TOO_FREQUENT),

    // --- 4. 短链接业务线 ---
    SHORT_LINK_NOT_EXIST(30001, MessageConstants.SHORT_LINK_NOT_EXIST),
    SHORT_LINK_IS_BANNED(30002, MessageConstants.SHORT_LINK_IS_BANNED),

    SHORT_CODE_RESERVED(30003, MessageConstants.SHORT_CODE_CUSTOM_RESERVED),
    SHORT_CODE_OCCUPIED(30004, MessageConstants.SHORT_CODE_CUSTOM_OCCUPIED),

    // --- 5. 管理员业务线 ---
    ADMIN_REQUIRED(40001, MessageConstants.ADMIN_REQUIRED),
    USER_ALREADY_BANNED(40002, MessageConstants.ADMIN_USER_BANNED),
    USER_ALREADY_ACTIVE(40003, MessageConstants.ADMIN_USER_ACTIVE),
    LINK_ALREADY_BANNED(40004, MessageConstants.ADMIN_LINK_BANNED),
    LINK_ALREADY_ACTIVE(40005, MessageConstants.ADMIN_LINK_ACTIVE);



    private final int code;
    private final String message;
}