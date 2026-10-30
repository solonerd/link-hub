package com.linkhub.agent.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 将 Agent 对话历史持久化到 Redis，按 memoryId（通常为登录用户 id）隔离会话。
 * <p>
 * 为何不用 JVM 内存：多实例与进程重启后上下文会丢失；Redis 可共享并带 TTL。
 * 序列化走 LangChain4j 官方 JSON API，保证与 {@code MessageWindowChatMemory} 兼容。
 * Redis key 形如 {@code agent:chat:memory:{memoryId}}，TTL 由
 * {@code agent.chat-memory.ttl-days} 控制（默认 7 天），避免闲置会话长期占内存。
 */
@Component
@RequiredArgsConstructor
public class RedisChatMemoryStore implements ChatMemoryStore {

    private static final String KEY_PREFIX = "agent:chat:memory:";

    private final StringRedisTemplate stringRedisTemplate;

    /** 闲置会话在 Redis 中的保留天数；每次 update 会刷新 TTL。 */
    @Value("${agent.chat-memory.ttl-days:7}")
    private long ttlDays;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String json = stringRedisTemplate.opsForValue().get(key(memoryId));
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        return ChatMessageDeserializer.messagesFromJson(json);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String json = ChatMessageSerializer.messagesToJson(messages);
        stringRedisTemplate.opsForValue().set(
                key(memoryId), json, Duration.ofDays(ttlDays));
    }

    @Override
    public void deleteMessages(Object memoryId) {
        stringRedisTemplate.delete(key(memoryId));
    }

    private String key(Object memoryId) {
        return KEY_PREFIX + memoryId;
    }
}