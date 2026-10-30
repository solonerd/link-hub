package com.linkhub.common;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "jwt") //
public class JwtProperties {

    private String secretKey;
    private long expirationTime;
    private long refreshExpirationTime;
    private int filterOrder = 1;
}