package com.linkhub.agent.config;

import cn.hutool.crypto.digest.DigestUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RagConfig {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${elasticsearch.server-url:http://localhost:9200}")
    private String esServerUrl;

    @Value("${elasticsearch.index-name:linkhub_faq_index}")
    private String indexName;

    private static final String FAQ_HASH_REDIS_KEY = "rag:faq:md5";
    private static final int RRF_K = 60; // RRF 标准平滑常数
    private static final int TOP_K = 4;  // 最终送给大模型的文档块数量

    @Bean(destroyMethod = "close")
    public RestClient elasticsearchRestClient() {
        return RestClient.builder(HttpHost.create(esServerUrl)).build();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(RestClient elasticsearchRestClient) {
        return ElasticsearchEmbeddingStore.builder()
                .restClient(elasticsearchRestClient)
                .indexName(indexName)
                .build();
    }

    @Bean
    public ContentRetriever contentRetriever(
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore,
            RestClient elasticsearchRestClient) {

        // 1. 启动时增量向量化写入 ES（MD5 比对防重）
        try (InputStream in = new ClassPathResource("docs/faq.md").getInputStream()) {
            byte[] bytes = in.readAllBytes();
            String currentMd5 = DigestUtil.md5Hex(bytes);
            String savedMd5 = stringRedisTemplate.opsForValue().get(FAQ_HASH_REDIS_KEY);

            if (!currentMd5.equals(savedMd5)) {
                log.info("检测到 FAQ 知识库首次加载或已变更，开始向量化并写入 Elasticsearch...");
                try {
                    embeddingStore.removeAll();
                } catch (Exception ignored) {
                }

                Document document = new TextDocumentParser().parse(new ByteArrayInputStream(bytes));
                EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                        .documentSplitter(DocumentSplitters.recursive(300, 50))
                        .embeddingModel(embeddingModel)
                        .embeddingStore(embeddingStore)
                        .build();

                ingestor.ingest(document);
                stringRedisTemplate.opsForValue().set(FAQ_HASH_REDIS_KEY, currentMd5);
                log.info("RAG 知识库写入 ES 完成！MD5={}", currentMd5);
            } else {
                log.info("FAQ 知识库未变更（MD5={}），直接复用 Elasticsearch 已有向量索引", currentMd5);
            }
        } catch (Exception e) {
            log.error("初始化 ES RAG 知识库异常，请检查 Elasticsearch ({}) 或 docs/faq.md 状态", esServerUrl, e);
        }

        // 2. 第一路检索器：密集向量语义检索 (Dense Vector kNN)
        ContentRetriever vectorRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(TOP_K)
                .minScore(0.55)
                .build();

        // 3. 返回混合检索器：向量检索 + ES BM25 倒排索引检索 + RRF 融合重排
        return (Query query) -> {
            List<Content> vectorHits = vectorRetriever.retrieve(query);
            List<Content> bm25Hits = searchByBm25(elasticsearchRestClient, query.text(), TOP_K);
            return fuseWithRrf(vectorHits, bm25Hits, TOP_K);
        };
    }

    /**
     * 第二路检索：直接调用 ES 倒排索引进行 BM25 关键字匹配
     * （LangChain4j 写入 ES 时，文本默认存储在 "text" 字段中）
     */
    private List<Content> searchByBm25(RestClient restClient, String queryText, int size) {
        List<Content> results = new ArrayList<>();
        try {
            Map<String, Object> dsl = Map.of(
                    "size", size,
                    "_source", List.of("text"),
                    "query", Map.of("match", Map.of("text", queryText))
            );

            Request request = new Request("POST", "/" + indexName + "/_search");
            request.setJsonEntity(objectMapper.writeValueAsString(dsl));

            Response response = restClient.performRequest(request);
            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            JsonNode hits = objectMapper.readTree(body).path("hits").path("hits");

            for (JsonNode hit : hits) {
                String text = hit.path("_source").path("text").asText(null);
                if (text != null && !text.isBlank()) {
                    results.add(Content.from(TextSegment.from(text)));
                }
            }
        } catch (Exception e) {
            log.warn("BM25 关键字检索降级，仅使用向量检索结果: {}", e.getMessage());
        }
        return results;
    }

    /**
     * RRF (Reciprocal Rank Fusion) 倒数排名融合算法：
     * score(d) = 1 / (60 + rank_vector) + 1 / (60 + rank_bm25)
     */
    private List<Content> fuseWithRrf(List<Content> vectorHits, List<Content> bm25Hits, int topK) {
        Map<String, Double> rrfScores = new LinkedHashMap<>();
        Map<String, Content> contentMap = new LinkedHashMap<>();

        for (int i = 0; i < vectorHits.size(); i++) {
            Content c = vectorHits.get(i);
            String key = c.textSegment().text();
            contentMap.putIfAbsent(key, c);
            rrfScores.merge(key, 1.0 / (RRF_K + i + 1), Double::sum);
        }

        for (int i = 0; i < bm25Hits.size(); i++) {
            Content c = bm25Hits.get(i);
            String key = c.textSegment().text();
            contentMap.putIfAbsent(key, c);
            rrfScores.merge(key, 1.0 / (RRF_K + i + 1), Double::sum);
        }

        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> contentMap.get(e.getKey()))
                .toList();
    }
}