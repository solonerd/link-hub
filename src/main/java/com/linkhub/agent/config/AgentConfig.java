package com.linkhub.agent.config;

import com.linkhub.agent.ShortLinkAgent;
import com.linkhub.agent.tool.ShortLinkTools;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 组装 ShortLinkAgent：同步/流式模型、按用户隔离的滑动窗口记忆、工具与 RAG 检索器。
 * <p>
 * 记忆链路：{@code chatMemoryProvider} 为每个 memoryId 构建
 * {@link MessageWindowChatMemory}（最多 20 条），
 * 底层通过注入的 {@link ChatMemoryStore}（实现为 Redis）读写，重启后可续聊。
 */
@Configuration
public class AgentConfig {

    /** 窗口内保留的最大消息条数；超出部分由 MessageWindowChatMemory 丢弃。 */
    private static final int MAX_MESSAGES = 20;

    @Bean
    public ShortLinkAgent shortLinkAgent(
            ChatLanguageModel model,
            StreamingChatLanguageModel streamingModel,
            ShortLinkTools tools,
            ContentRetriever contentRetriever,
            ChatMemoryStore chatMemoryStore) {

        return AiServices.builder(ShortLinkAgent.class)
                .chatLanguageModel(model)
                .streamingChatLanguageModel(streamingModel)
                // memoryId 通常即 userId；Store 负责跨请求持久化
                .chatMemoryProvider(memoryId ->
                        MessageWindowChatMemory.builder()
                                .id(memoryId)
                                .maxMessages(MAX_MESSAGES)
                                .chatMemoryStore(chatMemoryStore)
                                .build())
                .tools(tools)
                .contentRetriever(contentRetriever)
                .build();
    }
}