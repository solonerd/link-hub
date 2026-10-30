package com.linkhub.utils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 解析。
 * <p>
 * 默认仅使用 {@link HttpServletRequest#getRemoteAddr()}，不信任可由客户端伪造的
 * {@code X-Forwarded-For} / {@code X-Real-IP}，避免限流被绕过。
 * 仅在可信反向代理后开启 {@code client-ip.trust-forwarded-headers=true}。
 */
public final class ClientIpUtil {
    private static volatile boolean trustForwardedHeaders = false;

    private ClientIpUtil() {}

    /** 由配置启动时注入；默认 {@code false}（只用 remoteAddr）。 */
    public static void setTrustForwardedHeaders(boolean trust) {
        trustForwardedHeaders = trust;
    }

    public static boolean isTrustForwardedHeaders() {
        return trustForwardedHeaders;
    }

    /**
     * 解析客户端 IP。未开启信任转发头时忽略 XFF，防止伪造 IP 刷限流。
     */
    public static String get(HttpServletRequest request) {
        if (!trustForwardedHeaders) {
            String remote = request.getRemoteAddr();
            return remote == null ? "unknown" : remote;
        }
        String ip = header(request, "X-Forwarded-For");
        if (ip == null) ip = header(request, "X-Real-IP");
        if (ip == null) ip = request.getRemoteAddr();
        if (ip == null) return "unknown";
        int comma = ip.indexOf(',');
        if (comma > 0) ip = ip.substring(0, comma).trim();
        return ip;
    }

    private static String header(HttpServletRequest request, String name) {
        String v = request.getHeader(name);
        if (v == null || v.isBlank() || "unknown".equalsIgnoreCase(v)) return null;
        return v.trim();
    }
}
