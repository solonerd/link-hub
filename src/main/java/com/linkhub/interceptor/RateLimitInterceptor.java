package com.linkhub.interceptor;

import com.linkhub.common.ResultCode;
import com.linkhub.utils.ClientIpUtil;
import com.linkhub.exception.RateLimitException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * 短链跳转限流：Redis 固定窗口（每分钟）。
 * <p>
 * 维度：同一 IP + 短码、同一 IP 全局。IP 经 {@link ClientIpUtil} 解析，
 * 默认不信任 XFF，降低伪造绕过风险。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    private static final int PER_IP_PER_SHORT_CODE_LIMIT = 30;
    private static final int PER_IP_GLOBAL_LIMIT = 120;
    private static final int TIME_WINDOW_SECONDS = 60;

    private static final String RATE_LIMIT_IP_CODE_PREFIX = "rate_limit:ip_code:";
    private static final String RATE_LIMIT_IP_GLOBAL_PREFIX = "rate_limit:ip_global:";

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws Exception {

        if (!HttpMethod.GET.matches(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();
        if (!path.startsWith("/redirect/")) {
            return true;
        }
        String shortCode = path.substring("/redirect/".length());
        String clientIp = ClientIpUtil.get(request);

        String ipCodeKey = RATE_LIMIT_IP_CODE_PREFIX + clientIp + ":" + shortCode;
        Long ipCodeCount = stringRedisTemplate.opsForValue().increment(ipCodeKey);
        if (ipCodeCount == null) ipCodeCount = 0L;

        if (ipCodeCount == 1) {
            stringRedisTemplate.expire(ipCodeKey, Duration.ofSeconds(TIME_WINDOW_SECONDS));
        }

        if (ipCodeCount > PER_IP_PER_SHORT_CODE_LIMIT) {
            log.warn("限流：同一IP({}) + 同一短码({}) 超过限制，当前次数: {}", clientIp, shortCode, ipCodeCount);
            throw new RateLimitException(ResultCode.TOO_MANY_REQUESTS);
        }

        String ipGlobalKey = RATE_LIMIT_IP_GLOBAL_PREFIX + clientIp;
        Long ipGlobalCount = stringRedisTemplate.opsForValue().increment(ipGlobalKey);
        if (ipGlobalCount == null) ipGlobalCount = 0L;

        if (ipGlobalCount == 1) {
            stringRedisTemplate.expire(ipGlobalKey, Duration.ofSeconds(TIME_WINDOW_SECONDS));
        }

        if (ipGlobalCount > PER_IP_GLOBAL_LIMIT) {
            log.warn("限流：同一IP({}) 全局超过限制，当前次数: {}", clientIp, ipGlobalCount);
            throw new RateLimitException(ResultCode.TOO_MANY_REQUESTS);
        }

        return true;
    }
}
