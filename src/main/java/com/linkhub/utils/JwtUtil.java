package com.linkhub.utils;

import com.linkhub.common.JwtProperties;
import com.linkhub.config.security.CustomUserDetails;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

/**
 * JWT 工具：HMAC 签发/验签，以 userId 为 subject 身份锚点，区分 access 与 refresh（claim {@code type}）。
 */
@Component
@RequiredArgsConstructor
public class JwtUtil {

    public static final String CLAIM_TYPE = "type";
    public static final String CLAIM_USERNAME = "uname";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final JwtProperties jwtProperties;

    /** 启动时生成一次，避免每次签发/解析都重新构造 */
    private SecretKey signInKey;

    @PostConstruct
    void initKey() {
        byte[] keyBytes = jwtProperties.getSecretKey().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("jwt.secret-key 至少需要 32 字节（256 位）");
        }
        this.signInKey = Keys.hmacShaKeyFor(keyBytes);
    }

    // ==================== 解析 ====================

    /**
     * 验签 + 过期校验，只解析一次，过滤器里直接用这个。
     * 失败会抛 JwtException（过期是其子类 ExpiredJwtException）。
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signInKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(parseClaims(token));
    }

    /** 提取不可变的用户主键 userId（核心身份锚点） */
    public Long extractUserId(String token) {
        return Long.parseLong(extractClaim(token, Claims::getSubject));
    }

    /** 提取生成 Token 时的用户名快照（仅用于日志或展示） */
    public String extractUsername(String token) {
        return extractClaim(token, c -> c.get(CLAIM_USERNAME, String.class));
    }

    public String extractTokenType(String token) {
        return extractClaim(token, c -> c.get(CLAIM_TYPE, String.class));
    }

    public boolean isAccessToken(String token) {
        return TYPE_ACCESS.equals(extractTokenType(token));
    }

    public boolean isRefreshToken(String token) {
        return TYPE_REFRESH.equals(extractTokenType(token));
    }

    /** 已经拿到 Claims 时用这个，避免重复解析 */
    public static boolean isAccessToken(Claims claims) {
        return TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class));
    }

    public static boolean isRefreshToken(Claims claims) {
        return TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class));
    }

    // ==================== 签发 ====================

    public String generateAccessToken(UserDetails userDetails) {
        return buildToken(userDetails, jwtProperties.getExpirationTime(), TYPE_ACCESS);
    }

    public String generateRefreshToken(UserDetails userDetails) {
        return buildToken(userDetails, jwtProperties.getRefreshExpirationTime(), TYPE_REFRESH);
    }

    public String generateToken(UserDetails userDetails) {
        return generateAccessToken(userDetails);
    }

    private String buildToken(UserDetails userDetails, long ttlMs, String type) {
        if (!(userDetails instanceof CustomUserDetails cud) || cud.getUserId() == null) {
            throw new IllegalArgumentException("签发 Token 必须使用包含 userId 的 CustomUserDetails");
        }
        Date now = new Date();
        JwtBuilder builder = Jwts.builder()
                .claim(CLAIM_TYPE, type)
                .subject(String.valueOf(cud.getUserId()))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMs));
        if (cud.getUsername() != null) {
            builder.claim(CLAIM_USERNAME, cud.getUsername());
        }
        return builder.signWith(signInKey).compact();
    }
}