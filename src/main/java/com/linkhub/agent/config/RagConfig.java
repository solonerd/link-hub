package com.linkhub.agent.config;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.TextDocumentParser; // 🌟 引入解析器
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;

/**
 * RAG：启动时将 classpath:docs/faq.md 切块向量化写入内存向量库。
 * 小项目可接受；生产建议持久化存储以避免重复消耗 embedding 配额。
 */
@Slf4j
@Configuration
public class RagConfig {

    @Bean
    public ContentRetriever contentRetriever(EmbeddingModel embeddingModel) {
        log.info("开始初始化 RAG 知识库...");

        EmbeddingStore<dev.langchain4j.data.segment.TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

        try {
            // InputStream 同时兼容 IDE 运行与打包后的 jar
            try (InputStream inputStream = new ClassPathResource("docs/faq.md").getInputStream()) {

                TextDocumentParser parser = new TextDocumentParser();
                Document document = parser.parse(inputStream);

                EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                        .documentSplitter(DocumentSplitters.recursive(300, 50))
                        .embeddingModel(embeddingModel)
                        .embeddingStore(embeddingStore)
                        .build();

                ingestor.ingest(document);
                log.info("RAG 知识库初始化完成！");
            }

        } catch (Exception e) {
            log.error("加载 FAQ 文档失败，请检查 resources/docs/faq.md 是否存在", e);
        }

        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(2)
                .minScore(0.6)
                .build();
    }
}