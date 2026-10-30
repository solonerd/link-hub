package com.linkhub.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RabbitMQ 发布确认与路由失败回调。
 * NACK 时目前仅移除 pending 并打日志，生产可扩展为重试/落库补偿。
 */
@Slf4j
@Configuration
public class RabbitMQConfirmConfig {

    private final RabbitTemplate rabbitTemplate;

    /** msgId → CorrelationData，便于确认回调后清理或补偿 */
    private final ConcurrentHashMap<String, CorrelationData> pendingConfirms = new ConcurrentHashMap<>();

    public RabbitMQConfirmConfig(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostConstruct
    public void init() {
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            String msgId = correlationData != null ? correlationData.getId() : "unknown";
            if (ack) {
                log.debug("消息确认成功: {}", msgId);
                pendingConfirms.remove(msgId);
            } else {
                log.error("消息确认失败: {}，原因: {}", msgId, cause);
                pendingConfirms.remove(msgId);
            }
        });

        rabbitTemplate.setReturnsCallback(returned -> {
            log.error("消息路由失败: Exchange={}, RoutingKey={}, 返回码={}, 原因={}",
                    returned.getExchange(),
                    returned.getRoutingKey(),
                    returned.getReplyCode(),
                    returned.getReplyText()
            );
        });
    }

    /** 发送前生成 CorrelationData，便于与 confirm 回调关联。 */

    public CorrelationData generateCorrelationData(String msgId) {
        CorrelationData data = new CorrelationData(msgId);
        pendingConfirms.put(msgId, data);
        return data;
    }
}