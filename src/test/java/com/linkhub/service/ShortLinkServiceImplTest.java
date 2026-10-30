package com.linkhub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkhub.common.Result;
import com.linkhub.dto.ShortLinkCreateDTO;
import com.linkhub.entity.ShortLink;
import com.linkhub.mapper.ClickLogMapper;
import com.linkhub.mapper.ShortLinkMapper;
import com.linkhub.service.BloomFilterService;
import com.linkhub.service.ClickStatisticsService;
import com.linkhub.service.impl.ShortLinkServiceImpl;
import com.linkhub.utils.SecurityUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShortLinkServiceImplTest {

    @Mock private ShortLinkMapper shortLinkMapper;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private BloomFilterService bloomFilterService;
    @Mock private RedissonClient redissonClient;
    @Mock private ClickStatisticsService clickStatisticsService;
    @Mock private ObjectMapper objectMapper;
    @Mock private ClickLogMapper clickLogMapper;
    @Mock private RLock rLock;

    @InjectMocks
    private ShortLinkServiceImpl shortLinkService;

    private MockedStatic<SecurityUtil> securityUtilMockedStatic;

    @BeforeEach
    void setUp() {
        // Mock SecurityUtil 静态方法，假装用户已登录
        securityUtilMockedStatic = mockStatic(SecurityUtil.class);
        securityUtilMockedStatic.when(SecurityUtil::getCurrentUserId).thenReturn(1001L);
    }

    @AfterEach
    void tearDown() {
        securityUtilMockedStatic.close();
    }

    @Test
    @DisplayName("跳转逻辑：布隆过滤器判断不存在，直接返回 null 防穿透")
    void testRedirect_BloomFilterBlock() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String shortCode = "notExist";

        // Redis 缓存未命中
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("short_link:" + shortCode)).thenReturn(null);
        // 布隆过滤器返回不存在
        when(bloomFilterService.mightContain(shortCode)).thenReturn(false);

        String result = shortLinkService.redirectToOriginalUrl(shortCode, request);
        assertNull(result, "布隆过滤器拦截后应返回 null");
        verify(shortLinkMapper, never()).selectOne(any()); // 绝不查库
    }

    @Test
    @DisplayName("跳转逻辑：缓存未命中，获取分布式锁查库重建缓存 (防击穿)")
    void testRedirect_CacheMiss_DBHit() throws InterruptedException {
        // 🌟 优化：完善 Request 细节
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.100");
        request.addHeader("User-Agent", "Mozilla/5.0");

        String shortCode = "aX9kP2";
        String originalUrl = "https://www.google.com";

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("short_link:" + shortCode)).thenReturn(null);
        when(bloomFilterService.mightContain(shortCode)).thenReturn(true);

        when(redissonClient.getLock("lock:shortcode:" + shortCode)).thenReturn(rLock);
        when(rLock.tryLock(300, 500, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true); // 确保能执行 unlock

        ShortLink dbLink = new ShortLink();
        dbLink.setShortCode(shortCode);
        dbLink.setOriginalUrl(originalUrl);
        dbLink.setValidDate(LocalDateTime.now().plusDays(10));
        when(shortLinkMapper.selectOne(any())).thenReturn(dbLink);

        String result = shortLinkService.redirectToOriginalUrl(shortCode, request);

        assertEquals(originalUrl, result);
        verify(shortLinkMapper, times(1)).selectOne(any());
        // 🌟 验证传递的 IP 和 UA 是真实的
        verify(clickStatisticsService, times(1)).recordClick(eq(shortCode), eq("192.168.1.100"), eq("Mozilla/5.0"));
        verify(rLock, times(1)).unlock();
    }

    @Test
    @DisplayName("生成短链：测试自定义短码被保留词拦截")
    void testGenerate_WithReservedCustomCode() {
        ShortLinkCreateDTO dto = new ShortLinkCreateDTO();
        dto.setOriginalUrl("https://example.com");
        dto.setCustomCode("admin");

        Result<String> result = shortLinkService.generateShortLink(dto);
        assertEquals(30003, result.getCode(), "应该返回短码保留错误码");
    }
}