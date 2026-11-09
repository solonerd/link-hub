// 路径：./src/main/java/com/linkhub/controller/AgentController.java
package com.linkhub.controller;

import com.linkhub.agent.ShortLinkAgent;
import com.linkhub.utils.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
@Tag(name = "AI 助手", description = "自然语言管理短链")
public class AgentController {

    private final ShortLinkAgent shortLinkAgent;

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "与短链助手对话 (流式 SSE)")
    public Flux<String> chat(@Valid @RequestBody ChatRequest request) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("traceId", traceId);
        Map<String, String> contextMap = MDC.getCopyOfContextMap();

        try {
            Long userId = SecurityUtil.getCurrentUserId();
            if (userId == null) {
                return Flux.just("请先登录后再使用 AI 助手");
            }

            log.info("收到 Agent 流式请求, userId={}, message={}", userId, request.message());

            return Flux.<String>create(sink -> {
                        shortLinkAgent.chatStream(userId, request.message())
                                .onPartialResponse(sink::next)
                                .onCompleteResponse(response -> sink.complete())
                                .onError(err -> {
                                    log.error("Agent 流式响应异常, userId={}", userId, err);
                                    sink.next("\n[抱歉，AI 助手服务暂时繁忙，请稍后重试]");
                                    sink.complete();
                                })
                                .start();

                        sink.onCancel(() -> log.info("客户端断开 SSE 连接, userId={}", userId));
                    })
                    .doOnEach(signal -> {
                        if (contextMap != null) {
                            MDC.setContextMap(contextMap);
                        }
                    })
                    .doFinally(signalType -> MDC.clear());
        } finally {
            // 主线程返回 Flux 后立即清除自身 MDC，防止 Tomcat 线程池复用串号
            MDC.clear();
        }
    }

    public record ChatRequest(@NotBlank(message = "消息不能为空") String message) {}
}