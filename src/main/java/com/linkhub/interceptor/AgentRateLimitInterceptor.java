package com.linkhub.interceptor;

import com.linkhub.common.ResultCode;
import com.linkhub.exception.RateLimitException;
import com.linkhub.utils.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * Agent 接口按用户限流，控制 LLM 调用费用与滥用。
 * 未登录请求交由 Security 返回 401，本拦截器直接放行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    private static final int PER_USER_LIMIT = 10;
    private static final int TIME_WINDOW_SECONDS = 60;
    private static final String PREFIX = "rate_limit:agent:user:";

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return true;
        }
        String key = PREFIX + userId;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count == null) count = 0L;
        if (count == 1) {
            stringRedisTemplate.expire(key, Duration.ofSeconds(TIME_WINDOW_SECONDS));
        }
        if (count > PER_USER_LIMIT) {
            log.warn("Agent 限流：userId={} 超过限制，当前次数={}", userId, count);
            throw new RateLimitException(ResultCode.TOO_MANY_REQUESTS);
        }
        return true;
    }
}