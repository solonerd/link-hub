package com.linkhub.config.security;

import com.linkhub.common.ResultCode;
import com.linkhub.service.LinkHubUserDetailsService;
import com.linkhub.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 鉴权过滤器：解析 Bearer access token，写入 SecurityContext。
 * <p>
 * 失败时仅设置 {@link #JWT_ERROR_ATTR}，不在此直接拦截，
 * 以便白名单路径在 token 过期时仍可放行，由授权层与 EntryPoint 统一响应。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 认证失败原因，交由 AuthenticationEntryPoint 映射为统一 Result */
    public static final String JWT_ERROR_ATTR = "JWT_ERROR";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final LinkHubUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                authenticate(token, request);
            } catch (ExpiredJwtException e) {
                request.setAttribute(JWT_ERROR_ATTR, ResultCode.TOKEN_EXPIRED);
            } catch (JwtException | IllegalArgumentException e) {
                log.warn("非法 JWT, uri={}, reason={}", request.getRequestURI(), e.getMessage());
                request.setAttribute(JWT_ERROR_ATTR, ResultCode.TOKEN_INVALID);
            } catch (UsernameNotFoundException e) {
                request.setAttribute(JWT_ERROR_ATTR, ResultCode.USER_NOT_EXIST);
            }
        }
        filterChain.doFilter(request, response);
    }

    /** 验签并校验 token 类型、账号状态后写入 SecurityContext。 */
    private void authenticate(String token, HttpServletRequest request) {
        Claims claims = jwtUtil.parseClaims(token);

        if (!JwtUtil.TYPE_ACCESS.equals(claims.get(JwtUtil.CLAIM_TYPE, String.class))) {
            // refresh 不得充当 access
            request.setAttribute(JWT_ERROR_ATTR, ResultCode.TOKEN_INVALID);
            return;
        }
        String subject = claims.getSubject();
        if (!StringUtils.hasText(subject)) {
            request.setAttribute(JWT_ERROR_ATTR, ResultCode.TOKEN_INVALID);
            return;
        }

        Long userId;
        try {
            userId = Long.parseLong(subject);
        } catch (NumberFormatException e) {
            // 兼容拦截旧版以 username 为 subject 的 token
            request.setAttribute(JWT_ERROR_ATTR, ResultCode.TOKEN_INVALID);
            return;
        }

        UserDetails userDetails = userDetailsService.loadUserById(userId);
        if (!userDetails.isEnabled() || !userDetails.isAccountNonLocked()) {
            // 封禁后即使旧 access 未过期也立即失效
            request.setAttribute(JWT_ERROR_ATTR, ResultCode.USER_ACCOUNT_LOCKED);
            return;
        }

        UsernamePasswordAuthenticationToken authToken =
                UsernamePasswordAuthenticationToken.authenticated(userDetails, null, userDetails.getAuthorities());
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authToken);
        SecurityContextHolder.setContext(context);
    }

    /** 从 Authorization: Bearer 头提取 token。 */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}