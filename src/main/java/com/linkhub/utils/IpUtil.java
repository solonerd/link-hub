package com.linkhub.utils;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * IP 获取工具类
 * 支持 Nginx、Apache、WebLogic 等常见代理场景
 */
@Slf4j
public final class IpUtil {

    private IpUtil() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /**
     * 用于获取客户端真实 IP 的 HTTP 请求头名称列表（按优先级从高到低排列）。
     *
     * 在反向代理、负载均衡或 CDN 环境下，直接使用 request.getRemoteAddr() 只能拿到代理服务器的 IP，
     * 无法获取原始客户端 IP。这些请求头会由代理服务器（如 Nginx、Apache、WebLogic）或 CDN 添加，
     * 用于传递原始客户端的 IP 地址。
     *
     * 注意：此列表中的头信息可能被伪造，生产环境中应结合代理服务器的可信配置（如设置 trusted proxies）
     * 来增强安全性。
     */
    private static final List<String> IP_HEADERS = List.of(

            /**
             * X-Real-IP：由 Nginx 等代理服务器设置的常用头。
             * 与 X-Forwarded-For 不同，它通常只包含一个 IP，即代理服务器直接接收到的客户端 IP。
             * 在配置 Nginx 时，通常会通过 proxy_set_header X-Real-IP $remote_addr; 来设置。
             * 在可信的代理环境下，此头比 X-Forwarded-For 更可靠，且不会被客户端伪造（如果 Nginx 覆盖了该头）。
             */
            "X-Real-IP",
            /**
             * X-Forwarded-For：事实上的行业标准。
             * 当请求经过多层代理时，每层代理都会将客户端的 IP 追加到该头的末尾，形成逗号分隔的 IP 列表。
             * 格式：X-Forwarded-For: 真实客户端IP, 代理1IP, 代理2IP
             * 提取时，通常取第一个（最左边）的 IP 作为原始客户端 IP。
             * ⚠️ 注意：此头可被客户端伪造，因此需要谨慎使用。
             */
            "X-Forwarded-For",
            /**
             * Proxy-Client-IP：由 Apache HTTP Server 的 mod_proxy 模块添加。
             * 属于较旧的代理头，常见于老版本的 Apache 部署。
             * 在新系统中很少使用，但保留以兼容老旧架构。
             */
            "Proxy-Client-IP",

            /**
             * WL-Proxy-Client-IP：由 Oracle WebLogic Server 的代理插件添加。
             * 用于在 WebLogic 集群或代理环境中传递客户端 IP。
             * 常见于使用 WebLogic 的企业级遗留系统。
             */
            "WL-Proxy-Client-IP",

            /**
             * HTTP_CLIENT_IP：某些 CGI 或老式网关（如 Zeus、Cisco 负载均衡器）使用的头。
             * 并非标准头，但偶尔会在特殊环境中出现。
             */
            "HTTP_CLIENT_IP",

            /**
             * HTTP_X_FORWARDED_FOR：与 X-Forwarded-For 相同，但由于某些 Servlet 容器（如 IBM WebSphere）
             * 在解析请求头时会将原始头名转换为 HTTP_ 前缀的大写形式，因此包含此备选名称。
             * 一般作为 X-Forwarded-For 的降级备选。
             */
            "HTTP_X_FORWARDED_FOR"
    );


    /**
     * 未知 IP 的标识（不区分大小写）
     */
    private static final List<String> UNKNOWN_IP_VALUES = Arrays.asList(
            "unknown", "127.0.0.1", "0:0:0:0:0:0:0:1", "::1"
    );

    /**
     * 获取真实客户端 IP
     *
     * @param request HTTP 请求对象
     * @return 真实 IP，如果无法获取则返回 "unknown"
     */
    public static String getRealIp(HttpServletRequest request) {
        if (request == null) {
            log.warn("HttpServletRequest 为 null，无法获取 IP");
            return "unknown";
        }

        // 1. 遍历所有可能的代理头
        for (String headerName : IP_HEADERS) {
            String ip = request.getHeader(headerName);
            if (isValidIp(ip)) {
                // 如果通过代理，可能返回多个 IP（如 "client, proxy1, proxy2"），取第一个
                String firstIp = extractFirstIp(ip);
                if (isValidIp(firstIp)) {
                    log.debug("从 Header [{}] 获取到真实 IP: {}", headerName, firstIp);
                    return firstIp;
                }
            }
        }

        // 2. 保底方案：直接从 RemoteAddr 获取
        String remoteAddr = request.getRemoteAddr();
        if (isValidIp(remoteAddr)) {
            log.debug("从 RemoteAddr 获取到 IP: {}", remoteAddr);
            return remoteAddr;
        }

        // 3. 实在获取不到，返回 unknown
        log.warn("无法获取客户端真实 IP");
        return "unknown";
    }

    /**
     * 检查 IP 是否有效（非空、非 unknown、非本地回环）
     */
    private static boolean isValidIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return false;
        }

        String trimmed = ip.trim();
        if (trimmed.isEmpty()) {
            return false;
        }

        // 检查是否是未知标识
        for (String unknown : UNKNOWN_IP_VALUES) {
            if (unknown.equalsIgnoreCase(trimmed)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 从逗号分隔的 IP 列表中提取第一个有效 IP
     * 例如："114.114.114.114, 192.168.1.1, 10.0.0.1" → "114.114.114.114"
     */
    private static String extractFirstIp(String ipList) {
        if (!StringUtils.hasText(ipList)) {
            return null;
        }

        String[] ips = ipList.split(",");
        for (String ip : ips) {
            String trimmed = ip.trim();
            if (isValidIp(trimmed)) {
                return trimmed;
            }
        }
        return null;
    }

    /**
     * 判断 IP 是否为内网 IP（用于统计时排除内网访问）
     */
    private static final List<String> INTERNAL_IP_PREFIXES = Arrays.asList(
            "10.", "192.168.", "172.16.", "172.17.", "172.18.", "172.19.",
            "172.20.", "172.21.", "172.22.", "172.23.", "172.24.", "172.25.",
            "172.26.", "172.27.", "172.28.", "172.29.", "172.30.", "172.31.",
            "127.", "0:0:0:0:0:0:0:1", "::1"
    );

    public static boolean isInternalIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return false;
        }
        for (String prefix : INTERNAL_IP_PREFIXES) {
            if (ip.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断 IP 是否为 IPv6
     */
    public static boolean isIpv6(String ip) {
        return StringUtils.hasText(ip) && ip.contains(":");
    }
}