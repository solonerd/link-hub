package com.linkhub.config;

import com.linkhub.interceptor.AgentRateLimitInterceptor;
import com.linkhub.interceptor.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册跳转限流与 Agent 限流拦截器。
 */
@Configuration
@RequiredArgsConstructor
public class RateLimitConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;
    private final AgentRateLimitInterceptor agentRateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/redirect/**")
                .excludePathPatterns(
                        "/auth/**",
                        "/doc.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/webjars/**",
                        "/error"
                );

        registry.addInterceptor(agentRateLimitInterceptor)
                .addPathPatterns("/agent/**");
    }
}