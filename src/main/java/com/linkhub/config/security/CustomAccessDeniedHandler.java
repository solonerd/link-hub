package com.linkhub.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkhub.common.Result;
import com.linkhub.common.ResultCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 统一认证/授权失败响应：401（未认证或 JWT 无效）与 403（权限不足），
 * 输出与业务一致的 {@link Result} JSON。
 */
@Component
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    /** 未认证：优先使用过滤器写入的细粒度错误码（如 TOKEN_EXPIRED）。 */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        ResultCode errorCode = (ResultCode) request.getAttribute(JwtAuthenticationFilter.JWT_ERROR_ATTR);
        if (errorCode == null) {
            errorCode = ResultCode.UNAUTHORIZED;
        }

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(errorCode)));
    }

    /** 已认证但角色不足。 */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(ResultCode.FORBIDDEN)));
    }
}
