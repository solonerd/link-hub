package com.linkhub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 客户端 IP 解析开关。
 * <p>
 * {@code client-ip.trust-forwarded-headers=false}（默认）时只用 remoteAddr；
 * 仅在应用前有可信反向代理、且代理会覆盖 XFF 时再设为 {@code true}。
 */
@Data
@ConfigurationProperties(prefix = "client-ip")
public class ClientIpProperties {
    /** 为 false 时忽略 X-Forwarded-For / X-Real-IP */
    private boolean trustForwardedHeaders = false;
}
