package com.linkhub.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linkhub.common.JwtProperties;
import com.linkhub.common.MessageConstants;
import com.linkhub.common.Result;
import com.linkhub.common.ResultCode;
import com.linkhub.config.MQConfig;
import com.linkhub.config.RabbitMQConfirmConfig;
import com.linkhub.config.security.CustomUserDetails;
import com.linkhub.dto.LoginDTO;
import com.linkhub.dto.RefreshTokenDTO;
import com.linkhub.dto.UserRegisterDTO;
import com.linkhub.dto.MailMessageDTO;
import com.linkhub.entity.User;
import com.linkhub.mapper.UserMapper;
import com.linkhub.service.AuthService;
import com.linkhub.service.LinkHubUserDetailsService;
import com.linkhub.utils.CodeGenerator;
import com.linkhub.utils.JwtUtil;
import com.linkhub.vo.TokenVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * 认证服务：邮箱验证码注册、登录、双 Token 刷新与登出。
 * <p>
 * Access / Refresh 分离；Refresh 落 Redis 便于吊销。验证码经 RabbitMQ 异步发信。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final RabbitMQConfirmConfig confirmConfig;
    private final JwtProperties jwtProperties;
    private final LinkHubUserDetailsService userDetailsService;

    private static final String VERIFY_CODE_KEY = "verify_code:";
    private static final String VERIFY_CODE_FREQ_KEY = "verify_code_freq:";
    private static final String REFRESH_KEY_PREFIX = "refresh:user:";

    /**
     * 邮箱登录。禁用/锁定与密码错误分开返回，避免统一吞成「密码错误」。
     */
    @Override
    public Result<TokenVO> login(LoginDTO loginDTO) {
        try {
            String email = loginDTO.getEmail();

            User user = userMapper.selectOne(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getEmail, email)
            );

            if (user == null) {
                return Result.error(ResultCode.USER_NOT_EXIST);
            }

            // 以 DB 中的 username 走 Spring Security 标准认证
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getUsername(), loginDTO.getPassword())
            );

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            return Result.success("登录成功", issueTokens(userDetails));

        } catch (DisabledException | LockedException e) {
            log.warn("登录失败，账号已锁定或禁用: {}", loginDTO.getEmail());
            return Result.error(ResultCode.USER_ACCOUNT_LOCKED);
        } catch (AuthenticationException e) {
            log.warn("登录失败，账号或密码错误: {}", loginDTO.getEmail());
            return Result.error(ResultCode.USER_PASSWORD_ERROR);
        } catch (Exception e) {
            log.error("登录时发生系统异常", e);
            return Result.error(ResultCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 校验 refresh token 类型，按 subject(userId) 读取 {@code refresh:user:{id}} 比对后，<br>
     * 用 loadUserById 回查最新用户信息并轮换签发；用户改名不影响刷新。
     */
    @Override
    public Result<TokenVO> refresh(RefreshTokenDTO dto) {
        String refreshToken = dto.getRefreshToken();
        try {
            if (!jwtUtil.isRefreshToken(refreshToken)) {
                return Result.error(ResultCode.UNAUTHORIZED);
            }
            Long userId = jwtUtil.extractUserId(refreshToken);
            String redisKey = REFRESH_KEY_PREFIX + userId;
            String saved = stringRedisTemplate.opsForValue().get(redisKey);
            if (saved == null || !saved.equals(refreshToken)) {
                return Result.error(ResultCode.UNAUTHORIZED);
            }
            UserDetails userDetails = userDetailsService.loadUserById(userId);
            return Result.success("刷新成功", issueTokens(userDetails));
        } catch (Exception e) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
    }

    /** 按 userId 删除 {@code refresh:user:{id}}，使后续刷新失败（access 仍需等自然过期）；token 无法解析时也视为登出成功。 */
    @Override
    public Result<String> logout(RefreshTokenDTO dto) {
        try {
            Long userId = jwtUtil.extractUserId(dto.getRefreshToken());
            stringRedisTemplate.delete(REFRESH_KEY_PREFIX + userId);
            return Result.success(MessageConstants.USER_LOGOUT_SUCCESS);
        } catch (Exception e) {
            return Result.success(MessageConstants.USER_LOGOUT_SUCCESS);
        }
    }

    /** 签发 access + refresh（subject = userId），refresh 写入 {@code refresh:user:{id}}（TTL = refresh 过期时间，单设备覆盖）。 */
    private TokenVO issueTokens(UserDetails userDetails) {
        String access = jwtUtil.generateAccessToken(userDetails);
        String refresh = jwtUtil.generateRefreshToken(userDetails);
        Long userId = ((CustomUserDetails) userDetails).getUserId();
        stringRedisTemplate.opsForValue().set(
                REFRESH_KEY_PREFIX + userId,
                refresh,
                Duration.ofMillis(jwtProperties.getRefreshExpirationTime())
        );
        return new TokenVO(access, refresh);
    }

    /**
     * 邮箱验证码注册；用户名由邮箱前缀规范化得到，冲突时追加随机后缀。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> register(UserRegisterDTO registerDTO) {
        String email = registerDTO.getEmail();
        String redisKey = VERIFY_CODE_KEY + email;

        String cachedCode = stringRedisTemplate.opsForValue().get(redisKey);
        if (cachedCode == null || !cachedCode.equals(registerDTO.getCode())) {
            return Result.error(ResultCode.VERIFY_CODE_ERROR);
        }

        boolean emailExists = userMapper.exists(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (emailExists) {
            return Result.error(ResultCode.USER_EMAIL_REGISTERED);
        }

        String emailPrefix = email.substring(0, email.indexOf('@'));
        String username = normalizeRegisterUsername(emailPrefix);

        if (userMapper.exists(new LambdaQueryWrapper<User>().eq(User::getUsername, username))) {
            String base = username.length() <= 15 ? username : username.substring(0, 15);
            username = base + "_" + CodeGenerator.generateMixedCode(4);
            while (userMapper.exists(new LambdaQueryWrapper<User>().eq(User::getUsername, username))) {
                username = base + "_" + CodeGenerator.generateMixedCode(4);
            }
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(registerDTO.getPassword()));
        user.setEmail(email);
        user.setRole("USER");

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            return Result.error(ResultCode.USER_EMAIL_REGISTERED);
        }

        stringRedisTemplate.delete(redisKey);
        return Result.success("注册成功，您的用户名是：" + username, null);
    }

    /**
     * 发送注册验证码：60s 频控（SETNX）+ Redis 存码 5 分钟，经 MQ 异步发信。
     */
    @Override
    public Result<String> sendVerificationCode(String email) {
        String freqKey = VERIFY_CODE_FREQ_KEY + email;

        Boolean isSuccess = stringRedisTemplate.opsForValue().setIfAbsent(freqKey, "1", Duration.ofSeconds(60));
        if (Boolean.FALSE.equals(isSuccess)) {
            return Result.error(ResultCode.VERIFY_CODE_SEND_FREQUENTLY);
        }

        String code = CodeGenerator.generateNumberCode(6);
        String redisKey = VERIFY_CODE_KEY + email;
        stringRedisTemplate.opsForValue().set(redisKey, code, Duration.ofMinutes(5));

        MailMessageDTO mailDTO = MailMessageDTO.builder()
                .email(email)
                .code(code)
                .scene("REGISTER")
                .timestamp(System.currentTimeMillis())
                .build();

        String msgId = IdUtil.simpleUUID();
        CorrelationData correlationData = confirmConfig.generateCorrelationData(msgId);

        rabbitTemplate.convertAndSend(
                MQConfig.MAIL_EXCHANGE,
                MQConfig.MAIL_ROUTING_KEY,
                mailDTO,
                correlationData
        );

        log.info("验证码消息已发送，msgId={}, email={}", msgId, email);
        return Result.success("验证码已发送", null);
    }

    /**
     * 注册用户名需满足 ^[a-zA-Z0-9_]{4,20}$（与资料更新规则一致）。
     * 邮箱前缀非法时回退为 user_ + 随机后缀。
     */
    private String normalizeRegisterUsername(String emailPrefix) {
        String cleaned = emailPrefix == null ? "" : emailPrefix.replaceAll("[^a-zA-Z0-9_]", "");
        if (cleaned.length() > 20) {
            cleaned = cleaned.substring(0, 20);
        }
        if (cleaned.length() < 4 || !cleaned.matches("^[a-zA-Z0-9_]{4,20}$")) {
            return "user_" + CodeGenerator.generateMixedCode(4);
        }
        return cleaned;
    }
}
