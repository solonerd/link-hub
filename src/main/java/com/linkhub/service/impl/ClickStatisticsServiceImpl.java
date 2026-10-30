package com.linkhub.service.impl;

import com.linkhub.entity.ClickLog;
import com.linkhub.mapper.ClickLogMapper;
import com.linkhub.mapper.ShortLinkMapper;
import com.linkhub.service.ClickStatisticsService;
import com.linkhub.service.IpRegionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 点击统计：异步累加 DB 计数、Redis 日趋势，并落库点击明细（含粗粒度地域与 UA）。
 * 异步失败仅记日志，不影响跳转主路径。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClickStatisticsServiceImpl implements ClickStatisticsService {

    private static final Duration STAT_TTL = Duration.ofDays(90);

    private final ShortLinkMapper shortLinkMapper;
    private final ClickLogMapper clickLogMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final IpRegionService ipRegionService;

    /** 异步记录一次点击；异常不影响 HTTP 跳转。 */
    @Override
    @Async
    public void recordClick(String shortCode, String ip, String userAgent) {
        try {
            shortLinkMapper.incrementClickNum(shortCode);

            String today = LocalDate.now().toString();
            String key = CLICK_STAT_PREFIX + shortCode + ":" + today;
            stringRedisTemplate.opsForValue().increment(key);
            stringRedisTemplate.expire(key, STAT_TTL);

            String[] region = ipRegionService.locate(ip);
            ClickLog row = new ClickLog();
            row.setShortCode(shortCode);
            row.setIp(ip);
            row.setCountry(region[0]);
            row.setProvince(region[1]);
            row.setCity(region[2]);
            row.setBrowser(browser(userAgent));
            row.setOs(os(userAgent));
            row.setUserAgent(cut(userAgent));
            row.setCreateTime(LocalDateTime.now());
            clickLogMapper.insert(row);
        } catch (Exception e) {
            log.error("记录点击失败, shortCode={}", shortCode, e);
        }
    }

    private static String browser(String ua) {
        if (ua == null) return "未知";
        if (ua.contains("Edg/")) return "Edge";
        if (ua.contains("Chrome/")) return "Chrome";
        if (ua.contains("Firefox/")) return "Firefox";
        if (ua.contains("Safari/")) return "Safari";
        return "其他";
    }

    private static String os(String ua) {
        if (ua == null) return "未知";
        if (ua.contains("Windows")) return "Windows";
        if (ua.contains("Android")) return "Android";
        if (ua.contains("iPhone") || ua.contains("iPad")) return "iOS";
        if (ua.contains("Mac OS")) return "macOS";
        if (ua.contains("Linux")) return "Linux";
        return "其他";
    }

    private static String cut(String s) {
        if (s == null) return null;
        return s.length() <= 512 ? s : s.substring(0, 512);
    }
}