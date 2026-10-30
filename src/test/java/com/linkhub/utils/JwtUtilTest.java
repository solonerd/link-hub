package com.linkhub.utils;

import com.linkhub.common.JwtProperties;
import com.linkhub.config.security.CustomUserDetails;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private CustomUserDetails user;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecretKey("0123456789abcdef0123456789abcdef");   // 32 字节
        props.setExpirationTime(60_000);          // access 1 分钟
        props.setRefreshExpirationTime(600_000);  // refresh 10 分钟

        jwtUtil = new JwtUtil(props);
        jwtUtil.initKey();

        user = new CustomUserDetails(
                1L,
                "tester",
                "ignored-password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")),
                true, true, true, true
        );
    }

    @Test
    @DisplayName("access token 类型 + userId subject + uname claim 正确")
    void accessToken() {
        String token = jwtUtil.generateAccessToken(user);
        assertTrue(jwtUtil.isAccessToken(token));
        assertFalse(jwtUtil.isRefreshToken(token));
        assertEquals(1L, jwtUtil.extractUserId(token));
        assertEquals("tester", jwtUtil.extractUsername(token));
    }

    @Test
    @DisplayName("refresh token 类型正确")
    void refreshToken() {
        String token = jwtUtil.generateRefreshToken(user);
        assertTrue(jwtUtil.isRefreshToken(token));
        assertFalse(jwtUtil.isAccessToken(token));
    }

    @Test
    @DisplayName("篡改签名后解析失败")
    void tamperedSignature() {
        String token = jwtUtil.generateAccessToken(user);
        String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "AAAA";
        assertThrows(Exception.class, () -> jwtUtil.parseClaims(tampered));
    }

    @Test
    @DisplayName("过期 token 抛 ExpiredJwtException")
    void expiredToken() throws InterruptedException {
        JwtProperties shortLived = new JwtProperties();
        shortLived.setSecretKey("0123456789abcdef0123456789abcdef");
        shortLived.setExpirationTime(1);          // 1 毫秒
        shortLived.setRefreshExpirationTime(1);
        JwtUtil shortJwt = new JwtUtil(shortLived);
        shortJwt.initKey();

        String token = shortJwt.generateAccessToken(user);
        Thread.sleep(50);
        assertThrows(ExpiredJwtException.class, () -> shortJwt.parseClaims(token));
    }

    @Test
    @DisplayName("secret 不足 32 字节时启动报错")
    void shortSecretRejected() {
        JwtProperties bad = new JwtProperties();
        bad.setSecretKey("too-short");
        JwtUtil util = new JwtUtil(bad);
        assertThrows(IllegalStateException.class, util::initKey);
    }
}