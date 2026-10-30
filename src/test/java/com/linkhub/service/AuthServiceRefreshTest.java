package com.linkhub.service;

import com.linkhub.common.JwtProperties;
import com.linkhub.common.Result;
import com.linkhub.config.RabbitMQConfirmConfig;
import com.linkhub.config.security.CustomUserDetails;
import com.linkhub.dto.RefreshTokenDTO;
import com.linkhub.mapper.UserMapper;
import com.linkhub.service.impl.AuthServiceImpl;
import com.linkhub.utils.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** refresh / logout 以 userId 定位 Redis key {@code refresh:user:{id}}。 */
@ExtendWith(MockitoExtension.class)
class AuthServiceRefreshTest {

    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private RabbitMQConfirmConfig confirmConfig;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtUtil jwtUtil;
    @Mock private JwtProperties jwtProperties;
    @Mock private LinkHubUserDetailsService userDetailsService;

    @InjectMocks
    private AuthServiceImpl authService;

    private static RefreshTokenDTO dto(String token) {
        RefreshTokenDTO dto = new RefreshTokenDTO();
        dto.setRefreshToken(token);
        return dto;
    }

    @Test
    @DisplayName("refresh：Redis 比对通过后按 userId 回查并轮换写回 refresh:user:{id}")
    void refresh_success() {
        CustomUserDetails user = new CustomUserDetails(1L, "tester", "", Collections.emptyList(),
                true, true, true, true);
        when(jwtUtil.isRefreshToken("old-rt")).thenReturn(true);
        when(jwtUtil.extractUserId("old-rt")).thenReturn(1L);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("refresh:user:1")).thenReturn("old-rt");
        when(userDetailsService.loadUserById(1L)).thenReturn(user);
        when(jwtUtil.generateAccessToken(user)).thenReturn("new-at");
        when(jwtUtil.generateRefreshToken(user)).thenReturn("new-rt");
        when(jwtProperties.getRefreshExpirationTime()).thenReturn(60_000L);

        Result<?> result = authService.refresh(dto("old-rt"));

        assertEquals(200, result.getCode());
        verify(valueOperations).set(eq("refresh:user:1"), eq("new-rt"), any(Duration.class));
        verify(userDetailsService, never()).loadUserByUsername(anyString());
    }

    @Test
    @DisplayName("refresh：与 Redis 存档不一致（已轮换/已吊销）返回 401")
    void refresh_mismatch() {
        when(jwtUtil.isRefreshToken("stale-rt")).thenReturn(true);
        when(jwtUtil.extractUserId("stale-rt")).thenReturn(1L);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("refresh:user:1")).thenReturn("other-rt");

        assertEquals(401, authService.refresh(dto("stale-rt")).getCode());
        verifyNoInteractions(userDetailsService);
    }

    @Test
    @DisplayName("refresh：传入 access token 直接拒绝")
    void refresh_rejectsAccessToken() {
        when(jwtUtil.isRefreshToken("at")).thenReturn(false);
        assertEquals(401, authService.refresh(dto("at")).getCode());
        verify(stringRedisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("logout：删除 refresh:user:{id}")
    void logout_deletesUserKey() {
        when(jwtUtil.extractUserId("rt")).thenReturn(5L);
        assertEquals(200, authService.logout(dto("rt")).getCode());
        verify(stringRedisTemplate).delete("refresh:user:5");
    }
}