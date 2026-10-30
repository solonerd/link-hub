package com.linkhub.config;

import com.linkhub.utils.ClientIpUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 将 {@link ClientIpProperties} 应用到 {@link ClientIpUtil} 静态开关。
 */
@Configuration
@EnableConfigurationProperties(ClientIpProperties.class)
@RequiredArgsConstructor
public class ClientIpConfig {

    private final ClientIpProperties clientIpProperties;

    @PostConstruct
    public void apply() {
        ClientIpUtil.setTrustForwardedHeaders(clientIpProperties.isTrustForwardedHeaders());
    }
}
