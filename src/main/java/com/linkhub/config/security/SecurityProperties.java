package com.linkhub.config.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;


import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "security")
public class SecurityProperties {

    private List<String> whitelist = new ArrayList<>();
}