package com.linkhub.agent;

import com.linkhub.agent.tool.ShortLinkTools;
import com.linkhub.common.Result;
import com.linkhub.config.security.CustomUserDetails;
import com.linkhub.service.ShortLinkService;
import com.linkhub.utils.SecurityUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 验证 Agent 工具在无 / 有残留 SecurityContext 的线程中，
 * 都以 @ToolMemoryId 的 userId 执行，并在结束后清理或还原原身份。
 */
@ExtendWith(MockitoExtension.class)
class ShortLinkToolsTest {

    @Mock
    private ShortLinkService shortLinkService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @InjectMocks
    private ShortLinkTools shortLinkTools;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("无 SecurityContext：执行期间注入 userId，结束后清空")
    void injectsUserIdWhenContextEmpty() {
        AtomicReference<Long> seen = new AtomicReference<>();
        when(shortLinkService.delShortLink(any())).thenAnswer(inv -> {
            seen.set(SecurityUtil.getCurrentUserId());
            return Result.success("ok");
        });

        String reply = shortLinkTools.deleteShortLink(42L, "abc123");

        assertEquals("删除成功", reply);
        assertEquals(42L, seen.get());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("残留其他用户身份：执行期间强制覆盖，结束后还原")
    void overridesAndRestoresStaleContext() {
        CustomUserDetails stale = new CustomUserDetails(7L, "other", "", Collections.emptyList(),
                true, true, true, true);
        Authentication prev = UsernamePasswordAuthenticationToken.authenticated(stale, null, stale.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(prev);

        AtomicReference<Long> seen = new AtomicReference<>();
        when(shortLinkService.delShortLink(any())).thenAnswer(inv -> {
            seen.set(SecurityUtil.getCurrentUserId());
            return Result.success("ok");
        });

        shortLinkTools.deleteShortLink(42L, "abc123");

        assertEquals(42L, seen.get());
        assertSame(prev, SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("userId 为空：直接提示登录，不调用业务")
    void nullUserIdShortCircuits() {
        String reply = shortLinkTools.deleteShortLink(null, "abc123");
        assertEquals("请先登录后再使用助手功能", reply);
        verifyNoInteractions(shortLinkService);
    }
}