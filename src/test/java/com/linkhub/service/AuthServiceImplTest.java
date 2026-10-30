package com.linkhub.service;

import com.linkhub.common.JwtProperties;
import com.linkhub.common.Result;
import com.linkhub.config.RabbitMQConfirmConfig;
import com.linkhub.dto.MailMessageDTO;
import com.linkhub.dto.UserRegisterDTO;
import com.linkhub.mapper.UserMapper;
import com.linkhub.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.connection.CorrelationData;
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

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private RabbitMQConfirmConfig confirmConfig;

    // 🌟 补全缺失的 Mock 依赖
    @Mock private AuthenticationManager authenticationManager;
    @Mock private com.linkhub.utils.JwtUtil jwtUtil;
    @Mock private JwtProperties jwtProperties;
    @Mock private LinkHubUserDetailsService userDetailsService;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    @DisplayName("注册：验证码错误或过期拦截")
    void testRegister_CodeError() {
        UserRegisterDTO dto = new UserRegisterDTO();
        dto.setEmail("test@gmail.com");
        dto.setPassword("123456");
        dto.setCode("999999");

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("verify_code:test@gmail.com")).thenReturn("123456"); // Redis 里的验证码不同

        Result<String> result = authService.register(dto);
        assertEquals(20001, result.getCode(), "验证码错误应被拦截");
        verify(userMapper, never()).insert(Collections.singleton(any()));
    }

    @Test
    @DisplayName("发送验证码：发送频次限制且成功推送到 MQ")
    void testSendVerificationCode_Success() {
        String email = "hello@example.com";

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        // 模拟通过频次校验 (setIfAbsent 返回 true)
        when(valueOperations.setIfAbsent(eq("verify_code_freq:" + email), eq("1"), any(Duration.class))).thenReturn(true);
        when(confirmConfig.generateCorrelationData(anyString())).thenReturn(new CorrelationData("mock-id"));

        Result<String> result = authService.sendVerificationCode(email);

        assertEquals(200, result.getCode());
        // 验证确实向 MQ 发送了消息
        verify(rabbitTemplate, times(1)).convertAndSend(
                eq("mail.direct.exchange"),
                eq("mail.send.routing"),
                any(MailMessageDTO.class),
                any(CorrelationData.class)
        );
        // 验证验证码被存入 Redis
        verify(valueOperations, times(1)).set(eq("verify_code:" + email), anyString(), any(Duration.class));
    }
}