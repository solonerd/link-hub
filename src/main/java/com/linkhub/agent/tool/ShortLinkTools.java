package com.linkhub.agent.tool;

import cn.hutool.crypto.digest.DigestUtil;
import com.linkhub.common.Result;
import com.linkhub.config.security.CustomUserDetails;
import com.linkhub.dto.ShortLinkCreateDTO;
import com.linkhub.dto.ShortLinkDelDTO;
import com.linkhub.service.ShortLinkService;
import com.linkhub.vo.ShortLinkStatsVO;
import com.linkhub.vo.UserStatsVO;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.function.Supplier;

/**
 * Agent 可调用工具：生成/删除短链、查询概览与统计。
 * 生成接口带短时幂等键，抑制 LLM 重复 tool-call；删除仍依赖 system prompt 二次确认。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShortLinkTools {

    private final ShortLinkService shortLinkService;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String IDEMPOTENT_KEY_PREFIX = "agent:idempotent:generate:";
    private static final long IDEMPOTENT_EXPIRE_SECONDS = 5;

    @Tool("生成短链接。当用户想把长链接转成短链接时调用。可指定标题。")
    public String generateShortLink(@ToolMemoryId Long userId,
                                    String originalUrl, String title) {
        return safeExecute(userId, () -> {
            // userId + URL MD5 作为幂等键，压缩 key 长度并防短时重复生成
            String urlHash = DigestUtil.md5Hex(originalUrl);
            String lockKey = IDEMPOTENT_KEY_PREFIX + userId + ":" + urlHash;

            Boolean isFirstRequest = stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", Duration.ofSeconds(IDEMPOTENT_EXPIRE_SECONDS));

            if (Boolean.FALSE.equals(isFirstRequest)) {
                log.warn("Agent 工具重复调用被拦截, userId={}, originalUrl={}", userId, originalUrl);
                return "系统检测到您正在重复生成相同的短链，操作已被拦截。请稍后在列表中查看。";
            }

            ShortLinkCreateDTO dto = new ShortLinkCreateDTO();
            dto.setOriginalUrl(originalUrl);
            dto.setTitle(title == null || title.isBlank() ? "未命名" : title);
            Result<String> result = shortLinkService.generateShortLink(dto);

            return result.getCode() == 200
                    ? "生成成功，短码为：" + result.getData()
                    : "生成失败：" + result.getMessage();
        });
    }

    @Tool("删除短链接。当用户明确要求删除某个短链时调用。")
    public String deleteShortLink(@ToolMemoryId Long userId, String shortCode) {
        return safeExecute(userId, () -> {
            ShortLinkDelDTO dto = new ShortLinkDelDTO();
            dto.setShortCode(shortCode);
            Result<String> result = shortLinkService.delShortLink(dto);
            return result.getCode() == 200 ? "删除成功" : "删除失败：" + result.getMessage();
        });
    }

    @Tool("查询当前用户的短链概览，包括总数量、总点击量、有效短链数。")
    public String getMyOverview(@ToolMemoryId Long userId) {
        return safeExecute(userId, () -> {
            Result<UserStatsVO> result = shortLinkService.getUserStats();
            if (result.getCode() != 200 || result.getData() == null) {
                return "查询失败：" + result.getMessage();
            }
            UserStatsVO s = result.getData();
            return String.format("你目前共有 %d 个短链，总点击量 %d，有效短链 %d 个。",
                    s.getTotalLinks(), s.getTotalClicks(), s.getActiveLinks());
        });
    }

    @Tool("查询某个短链的详细统计信息（点击量、趋势等）。")
    public String getLinkStats(@ToolMemoryId Long userId, String shortCode) {
        return safeExecute(userId, () -> {
            Result<ShortLinkStatsVO> result = shortLinkService.getLinkStats(shortCode, 7);
            if (result.getCode() != 200 || result.getData() == null) {
                return "查询失败或短链不存在";
            }
            ShortLinkStatsVO s = result.getData();
            return String.format("短链 %s（%s）当前点击量 %d，是否过期：%s",
                    s.getShortCode(), s.getTitle(), s.getClickNum(),
                    Boolean.TRUE.equals(s.getExpired()) ? "是" : "否");
        });
    }

    /** 统一登录校验、耗时日志与异常兜底，避免工具异常直接打断对话。 */
    private String safeExecute(Long userId, Supplier<String> action) {
        if (userId == null) {
            return "请先登录后再使用助手功能";
        }

        String traceId = MDC.get("traceId");
        // 工具线程可能无 SecurityContext 或残留旧身份：备份原值 → 强制覆盖为 @ToolMemoryId 的 userId → finally 还原
        Authentication prev = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails tempUser = new CustomUserDetails(
                userId, "agent_user", "", Collections.emptyList(),
                true, true, true, true);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken
                .authenticated(tempUser, null, tempUser.getAuthorities()));
        SecurityContextHolder.setContext(context);
        try {
            log.info("[{}] 工具执行开始, userId={}", traceId, userId);
            long start = System.currentTimeMillis();
            String result = action.get();
            log.info("[{}] 工具执行结束, 耗时={}ms, 结果={}", traceId, (System.currentTimeMillis() - start), result);
            return result;
        } catch (Exception e) {
            log.error("[{}] Agent 工具执行异常, userId={}", traceId, userId, e);
            return "操作失败：" + (e.getMessage() == null ? "系统异常" : e.getMessage());
        } finally {
            // 原本无身份则清空，原本有则还原，避免污染线程池
            if (prev == null) {
                SecurityContextHolder.clearContext();
            } else {
                SecurityContext restored = SecurityContextHolder.createEmptyContext();
                restored.setAuthentication(prev);
                SecurityContextHolder.setContext(restored);
            }
        }
    }
}