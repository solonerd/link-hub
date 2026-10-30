package com.linkhub.controller;

import com.linkhub.agent.ShortLinkAgent;
import com.linkhub.common.ResultCode;
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

/**
 * AI 助手 SSE 接口。流式回调在异步线程上执行，需显式传递/清理 MDC，避免 trace 串号。
 */
@Slf4j
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
@Tag(name = "AI 助手", description = "自然语言管理短链")
public class AgentController {

    private final ShortLinkAgent shortLinkAgent;

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "与短链助手对话 (流式 SSE)")
    public Flux<Object> chat(@Valid @RequestBody ChatRequest request) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("traceId", traceId);

        // 主线程复制 MDC，供异步流回调线程复用
        Map<String, String> contextMap = MDC.getCopyOfContextMap();

        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            MDC.clear();
            return Flux.just("未登录");
        }

        log.info("收到 Agent 流式请求, userId={}, message={}", userId, request.message());

        return Flux.create(sink -> {
                    shortLinkAgent.chatStream(userId, request.message())
                            .onPartialResponse(sink::next)
                            .onCompleteResponse(response -> sink.complete())
                            .onError(sink::error)
                            .start();

                    sink.onCancel(() -> log.info("客户端断开连接"));
                })
                .doOnEach(signal -> {
                    if (contextMap != null) {
                        MDC.setContextMap(contextMap);
                    }
                })
                // 流结束后清理，防止线程池复用串号
                .doFinally(signalType -> MDC.clear());
    }

    public record ChatRequest(@NotBlank(message = "消息不能为空") String message) {}
}