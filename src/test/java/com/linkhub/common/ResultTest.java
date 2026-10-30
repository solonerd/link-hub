package com.linkhub.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResultTest {

    @Test
    @DisplayName("success()：code=200，data 为空")
    void successEmpty() {
        Result<Object> r = Result.success();
        assertEquals(200, r.getCode());
        assertNull(r.getData());
    }

    @Test
    @DisplayName("success(data)：data 透传")
    void successData() {
        Result<String> r = Result.success("hello");
        assertEquals(200, r.getCode());
        assertEquals("hello", r.getData());
    }

    @Test
    @DisplayName("success(message, data)：message 覆盖默认")
    void successWithMessage() {
        Result<Integer> r = Result.success("已保存", 42);
        assertEquals(200, r.getCode());
        assertEquals("已保存", r.getMessage());
        assertEquals(42, r.getData());
    }

    @Test
    @DisplayName("error(ResultCode)：code 和 message 都来自枚举")
    void errorWithEnum() {
        Result<Object> r = Result.error(ResultCode.USER_NOT_EXIST);
        assertEquals(10001, r.getCode());
        assertEquals(MessageConstants.USER_NOT_EXIST, r.getMessage());
        assertNull(r.getData());
    }

    @Test
    @DisplayName("error(String)：默认 500")
    void errorWithString() {
        Result<Object> r = Result.error("出错了");
        assertEquals(500, r.getCode());
        assertEquals("出错了", r.getMessage());
    }

    @Test
    @DisplayName("error(code, message)：自定义 code")
    void errorWithCode() {
        Result<Object> r = Result.error(400, "参数错误");
        assertEquals(400, r.getCode());
        assertEquals("参数错误", r.getMessage());
    }
}