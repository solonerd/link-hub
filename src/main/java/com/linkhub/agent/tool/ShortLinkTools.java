// 路径：./src/main/java/com/linkhub/agent/tool/ShortLinkTools.java
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

@Slf4j
@Component
@RequiredArgsConstructor
public class ShortLinkTools {

    private final ShortLinkService shortLinkService;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String IDEMPOTENT_KEY_PREFIX = "agent:idempotent:generate:";
    private static final long IDEMPOTENT_EXPIRE_SECONDS = 30; // 升级为 30s，覆盖 LLM 超时重试窗口

    private static final String PENDING_DELETE_PREFIX = "agent:pending_delete:";
    private static final long PENDING_DELETE_TTL_MINUTES = 5;
    private static final long MIN_CONFIRM_INTERVAL_MS = 1500; // 至少间隔 1.5s，防 LLM 同轮自问自答连调绕过

    @Tool("生成短链接。当用户想把长链接转成短链接时调用。可指定标题。")
    public String generateShortLink(@ToolMemoryId Long userId,
                                    String originalUrl, String title) {
        return safeExecute("generateShortLink", userId, () -> {
            String urlHash = DigestUtil.md5Hex(originalUrl);
            String lockKey = IDEMPOTENT_KEY_PREFIX + userId + ":" + urlHash;

            Boolean isFirstRequest = stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", Duration.ofSeconds(IDEMPOTENT_EXPIRE_SECONDS));

            if (Boolean.FALSE.equals(isFirstRequest)) {
                log.warn("Agent 工具重复调用被拦截, userId={}, originalUrl={}", userId, originalUrl);
                return "系统检测到您刚刚已为该网址生成过短链，操作已防重拦截。请直接在短链列表中查看。";
            }

            ShortLinkCreateDTO dto = new ShortLinkCreateDTO();
            dto.setOriginalUrl(originalUrl);
            dto.setTitle(title == null || title.isBlank() ? "未命名" : title);
            Result<String> result = shortLinkService.generateShortLink(dto);

            return result.getCode() == 200
                    ? String.format("{\"status\":\"SUCCESS\",\"shortCode\":\"%s\",\"title\":\"%s\"}", result.getData(), dto.getTitle())
                    : "生成失败：" + result.getMessage();
        });
    }

    @Tool("删除短链接。首次调用会挂起待确认状态，仅在用户下一轮明确回复确认后二次调用才会真正执行删除。")
    public String deleteShortLink(@ToolMemoryId Long userId, String shortCode) {
        return safeExecute("deleteShortLink", userId, () -> {
            String pendingKey = PENDING_DELETE_PREFIX + userId + ":" + shortCode;
            String pendingTimestampStr = stringRedisTemplate.opsForValue().get(pendingKey);

            // 阶段 1：尚未挂起确认 -> 写入挂起状态并拦截，绝不直接删库
            if (pendingTimestampStr == null) {
                stringRedisTemplate.opsForValue().set(
                        pendingKey,
                        String.valueOf(System.currentTimeMillis()),
                        Duration.ofMinutes(PENDING_DELETE_TTL_MINUTES));
                return "【需要二次确认】已在服务端挂起短链 " + shortCode + " 的删除请求（5分钟内有效）。请向用户确认：您即将删除短链 " + shortCode + "，请回复【确认删除】以继续。";
            }

            // 防护：拦截 LLM 在同一次推理循环内连续调用两次工具试图绕过确认
            long elapsed = System.currentTimeMillis() - Long.parseLong(pendingTimestampStr);
            if (elapsed < MIN_CONFIRM_INTERVAL_MS) {
                log.warn("拦截 LLM 同轮自动连续调用 deleteShortLink, userId={}, shortCode={}, elapsed={}ms", userId, shortCode, elapsed);
                return "请等待用户亲自回复【确认删除】后再调用此工具，禁止在同一次回复中自动确认。";
            }

            // 阶段 2：用户已在下一轮对话确认 -> 消费挂起令牌并执行真实删除
            stringRedisTemplate.delete(pendingKey);
            ShortLinkDelDTO dto = new ShortLinkDelDTO();
            dto.setShortCode(shortCode);
            Result<String> result = shortLinkService.delShortLink(dto);
            return result.getCode() == 200 ? "删除成功" : "删除失败：" + result.getMessage();
        });
    }

    @Tool("查询当前用户的短链概览，包括总数量、总点击量、有效短链数。")
    public String getMyOverview(@ToolMemoryId Long userId) {
        return safeExecute("getMyOverview", userId, () -> {
            Result<UserStatsVO> result = shortLinkService.getUserStats();
            if (result.getCode() != 200 || result.getData() == null) {
                return "查询失败：" + result.getMessage();
            }
            UserStatsVO s = result.getData();
            return String.format("{\"totalLinks\":%d,\"totalClicks\":%d,\"activeLinks\":%d}",
                    s.getTotalLinks(), s.getTotalClicks(), s.getActiveLinks());
        });
    }

    @Tool("查询某个短链的详细统计信息（点击量、趋势等）。")
    public String getLinkStats(@ToolMemoryId Long userId, String shortCode) {
        return safeExecute("getLinkStats", userId, () -> {
            Result<ShortLinkStatsVO> result = shortLinkService.getLinkStats(shortCode, 7);
            if (result.getCode() != 200 || result.getData() == null) {
                return "查询失败或短链不存在";
            }
            ShortLinkStatsVO s = result.getData();
            return String.format("{\"shortCode\":\"%s\",\"title\":\"%s\",\"clickNum\":%d,\"expired\":%b}",
                    s.getShortCode(), s.getTitle(), s.getClickNum(), Boolean.TRUE.equals(s.getExpired()));
        });
    }

    private String safeExecute(String toolName, Long userId, Supplier<String> action) {
        if (userId == null) {
            return "请先登录后再使用助手功能";
        }

        String traceId = MDC.get("traceId");
        Authentication prev = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails tempUser = new CustomUserDetails(
                userId, "agent_user", "", Collections.emptyList(),
                true, true, true, true);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken
                .authenticated(tempUser, null, tempUser.getAuthorities()));
        SecurityContextHolder.setContext(context);
        long start = System.currentTimeMillis();
        try {
            log.info("[AUDIT][{}] tool={} start, userId={}", traceId, toolName, userId);
            String result = action.get();
            log.info("[AUDIT][{}] tool={} success, userId={}, cost={}ms, result={}",
                    traceId, toolName, userId, (System.currentTimeMillis() - start), result);
            return result;
        } catch (Exception e) {
            log.error("[AUDIT][{}] tool={} error, userId={}, cost={}ms",
                    traceId, toolName, userId, (System.currentTimeMillis() - start), e);
            return "操作失败：" + (e.getMessage() == null ? "系统异常" : e.getMessage());
        } finally {
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