package com.linkhub.config;

import lombok.RequiredArgsConstructor;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Redisson 客户端：优先集群节点，否则回落单机，密码取自 Spring RedisProperties。
 * 用于分布式锁与布隆过滤器。
 */
@Configuration
@RequiredArgsConstructor
public class RedissonConfig {

    private final RedisProperties redisProperties;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();

        RedisProperties.Cluster cluster = redisProperties.getCluster();
        List<String> clusterNodes = (cluster != null && cluster.getNodes() != null)
                ? cluster.getNodes()
                : List.of();

        String password = redisProperties.getPassword();

        if (clusterNodes.isEmpty()) {
            String host = redisProperties.getHost();
            int port = redisProperties.getPort();

            config.useSingleServer()
                    .setAddress("redis://" + host + ":" + port)
                    .setPassword(StringUtils.hasText(password) ? password : null);
        } else {
            String[] nodeAddresses = clusterNodes.stream()
                    .map(node -> node.startsWith("redis://") ? node : "redis://" + node)
                    .toArray(String[]::new);

            config.useClusterServers()
                    .addNodeAddress(nodeAddresses)
                    .setPassword(StringUtils.hasText(password) ? password : null);
        }

        return Redisson.create(config);
    }
}