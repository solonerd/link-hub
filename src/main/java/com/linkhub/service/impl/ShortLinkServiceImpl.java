package com.linkhub.service.impl;

import cn.hutool.core.util.HashUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkhub.common.MessageConstants;
import com.linkhub.common.Result;
import com.linkhub.common.ResultCode;
import com.linkhub.dto.ShortLinkCreateDTO;
import com.linkhub.dto.ShortLinkDelDTO;
import com.linkhub.dto.ShortLinkStatusDTO;
import com.linkhub.entity.ClickLog;
import com.linkhub.entity.ShortLink;
import com.linkhub.mapper.ClickLogMapper;
import com.linkhub.mapper.ShortLinkMapper;
import com.linkhub.service.BloomFilterService;
import com.linkhub.service.ClickStatisticsService;
import com.linkhub.service.ShortLinkService;
import com.linkhub.utils.Base62Util;
import com.linkhub.utils.ClientIpUtil;
import com.linkhub.utils.SecurityUtil;
import com.linkhub.vo.ClickLogVO;
import com.linkhub.vo.DailyClickVO;
import com.linkhub.vo.ShortLinkCacheVO;
import com.linkhub.vo.ShortLinkStatsVO;
import com.linkhub.vo.UserStatsVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 短链核心业务：生成、跳转、删除、统计与状态管理。
 * <p>
 * 跳转路径：Redis 缓存（含 status）→ 布隆过滤器 → Redisson 互斥回源 DB。
 * 封禁/批量删除在事务提交后再删缓存，避免并发回源写回旧状态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl extends ServiceImpl<ShortLinkMapper, ShortLink> implements ShortLinkService {

    private final ShortLinkMapper shortLinkMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final BloomFilterService bloomFilterService;
    private final RedissonClient redissonClient;
    private final ClickStatisticsService clickStatisticsService;
    private final ObjectMapper objectMapper;
    private final ClickLogMapper clickLogMapper;

    private static final String CACHE_KEY_PREFIX = "short_link:";
    private static final String LOCK_KEY_PREFIX = "lock:shortcode:";
    private static final int MAX_RETRY = 5;
    private static final int CACHE_EXPIRE_DAYS = 7;

    // ========================= 核心跳转 =========================

    /**
     * 短码跳转：缓存命中须校验过期与 status；未命中先经布隆过滤器再加锁回源，
     * 防止缓存击穿与封禁后仍被缓存命中绕过。
     *
     * @return 原链 URL；不存在、过期或已封禁时返回 {@code null}
     */
    @Override
    public String redirectToOriginalUrl(String shortCode, HttpServletRequest request) {
        String cacheKey = CACHE_KEY_PREFIX + shortCode;
        ShortLinkCacheVO hit = readCache(cacheKey);
        if (hit != null) {
            if (expired(hit.getValidDate())) {
                stringRedisTemplate.delete(cacheKey);
                return null;
            }
            // status 写入缓存，避免仅删 key 时的并发窗口导致封禁失效
            if (hit.getStatus() != null && hit.getStatus() != 0) {
                return null;
            }
            clickStatisticsService.recordClick(
                    shortCode,
                    ClientIpUtil.get(request),
                    request.getHeader("User-Agent"));
            return hit.getUrl();
        }

        // 布隆过滤器否定可直接拒绝，减少无效 DB 查询
        if (!bloomFilterService.mightContain(shortCode)) {
            return null;
        }

        RLock rLock = redissonClient.getLock(LOCK_KEY_PREFIX + shortCode);
        int tryCount = 0;
        while (tryCount < MAX_RETRY) {
            try {
                if (rLock.tryLock(300, 500, TimeUnit.MILLISECONDS)) {
                    try {
                        hit = readCache(cacheKey);
                        if (hit != null) {
                            if (expired(hit.getValidDate())) {
                                stringRedisTemplate.delete(cacheKey);
                                return null;
                            }
                            if (hit.getStatus() != null && hit.getStatus() != 0) {
                                return null;
                            }
                            clickStatisticsService.recordClick(
                                    shortCode,
                                    ClientIpUtil.get(request),
                                    request.getHeader("User-Agent"));
                            return hit.getUrl();
                        }

                        ShortLink link = shortLinkMapper.selectOne(new LambdaQueryWrapper<ShortLink>()
                                .eq(ShortLink::getShortCode, shortCode)
                                .eq(ShortLink::getStatus,0));
                        if (link == null || expired(link.getValidDate())) {
                            return null;
                        }

                        writeCache(cacheKey, link.getOriginalUrl(), link.getValidDate(), link.getStatus() == null ? 0 : link.getStatus());
                        bloomFilterService.addShortCode(shortCode);
                        clickStatisticsService.recordClick(
                                shortCode,
                                ClientIpUtil.get(request),
                                request.getHeader("User-Agent"));
                        return link.getOriginalUrl();
                    } finally {
                        if (rLock.isHeldByCurrentThread()) {
                            rLock.unlock();
                        }
                    }
                }
                Thread.sleep(100);
                tryCount++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                return null;
            }
        }
        return null;
    }

    // ========================= 生成 =========================

    /**
     * 生成短链：无自定义码时用雪花 ID + Base62；自定义码经保留字校验、
     * 布隆+DB 占用检查，并用分布式锁防止并发抢占。
     */
    @Override
    public Result<String> generateShortLink(ShortLinkCreateDTO createDTO) {
        String custom = createDTO.getCustomCode();
        String originalUrl = createDTO.getOriginalUrl();
        Long userId = SecurityUtil.getCurrentUserId();
        int days = Objects.requireNonNullElse(createDTO.getValidDays(), CACHE_EXPIRE_DAYS);
        String title = StrUtil.isNotBlank(createDTO.getTitle()) ? createDTO.getTitle() : "未命名";
        LocalDateTime validDate = LocalDateTime.now().plusDays(days);

        if (StrUtil.isBlank(custom)) {
            long id = IdUtil.getSnowflakeNextId();
            String shortCode = Base62Util.encodeBase62(id);
            return saveResultCode(shortCode, originalUrl, userId, title, validDate);
        }

        if (checkCode(custom)) {
            return Result.error(ResultCode.SHORT_CODE_RESERVED);
        }

        boolean used = false;
        if (bloomFilterService.mightContain(custom)) {
            Long cnt = shortLinkMapper.selectCount(
                    new LambdaQueryWrapper<ShortLink>().eq(ShortLink::getShortCode, custom));
            if (cnt > 0) used = true;
        }
        if (used) {
            return Result.error(ResultCode.SHORT_CODE_OCCUPIED);
        }

        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + custom);
        boolean locked = false;
        try {
            locked = lock.tryLock(0, 5, TimeUnit.SECONDS);
            if (!locked) {
                // 未拿到锁视为冲突，避免并发双写同一自定义码
                return Result.error(ResultCode.SHORT_CODE_OCCUPIED);
            }
            Long count = shortLinkMapper.selectCount(
                    new LambdaQueryWrapper<ShortLink>().eq(ShortLink::getShortCode, custom));
            if (count > 0) {
                return Result.error(ResultCode.SHORT_CODE_OCCUPIED);
            }
            return saveResultCode(custom, originalUrl, userId, title, validDate);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.error(ResultCode.INTERNAL_SERVER_ERROR);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // ========================= 删除 =========================

    /** 删除当前用户名下单条短链，缓存在事务提交后清理。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> delShortLink(ShortLinkDelDTO delDTO) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        ShortLink shortLink = shortLinkMapper.selectOne(new LambdaQueryWrapper<ShortLink>()
                .eq(ShortLink::getShortCode, delDTO.getShortCode())
                .eq(ShortLink::getUserId, userId));
        if (shortLink == null) {
            return Result.error(ResultCode.SHORT_LINK_NOT_EXIST);
        }
        shortLinkMapper.deleteById(shortLink.getId());
        deleteCacheAfterCommit(List.of(CACHE_KEY_PREFIX + shortLink.getShortCode()));
        return Result.success(MessageConstants.SHORT_LINK_DELETE_SUCCESS);
    }

    /**
     * 批量逻辑删除；缓存在 {@link #deleteCacheAfterCommit} 中于提交后清理，
     * 避免事务未提交时并发回源重建出旧数据。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> delShortLinks(List<ShortLinkDelDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return Result.error(ResultCode.SHORT_LINK_NOT_EXIST);
        }
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }

        List<String> codes = dtos.stream()
                .map(ShortLinkDelDTO::getShortCode)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList();
        if (codes.isEmpty()) {
            return Result.error(MessageConstants.SHORT_CODE_NOT_EMPTY);
        }

        List<ShortLink> links = shortLinkMapper.selectList(
                new LambdaQueryWrapper<ShortLink>()
                        .eq(ShortLink::getUserId, userId)
                        .in(ShortLink::getShortCode, codes));
        if (links.isEmpty()) {
            return Result.error(MessageConstants.OPERATION_FAILED);
        }

        List<Long> ownedIds = links.stream().map(ShortLink::getId).toList();
        int deleted = shortLinkMapper.deleteByIds(ownedIds);

        List<String> cacheKeys = links.stream()
                .map(link -> CACHE_KEY_PREFIX + link.getShortCode())
                .toList();
        deleteCacheAfterCommit(cacheKeys);

        return Result.success("删除成功，共 " + deleted + " 条", null);
    }

    // ========================= 统计 =========================

    /** 单条短链统计（日趋势、地域 Top），仅本人可读。 */
    @Override
    public Result<ShortLinkStatsVO> getLinkStats(String shortCode, int days) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        if (days <= 0 || days > 90) {
            days = 7;
        }
        ShortLink link = shortLinkMapper.selectOne(new LambdaQueryWrapper<ShortLink>()
                .eq(ShortLink::getShortCode, shortCode)
                .eq(ShortLink::getUserId, userId));
        if (link == null) {
            return Result.error(ResultCode.SHORT_LINK_NOT_EXIST);
        }
        ShortLinkStatsVO vo = new ShortLinkStatsVO();
        vo.setShortCode(link.getShortCode());
        vo.setOriginalUrl(link.getOriginalUrl());
        vo.setTitle(link.getTitle());
        vo.setClickNum(link.getClickNum() == null ? 0L : link.getClickNum());
        vo.setCreateTime(link.getCreateTime());
        vo.setValidDate(link.getValidDate());
        vo.setExpired(expired(link.getValidDate()));
        vo.setDailyTrend(buildDailyTrend(shortCode, days));
        vo.setTopRegions(clickLogMapper.topProvinces(shortCode));
        return Result.success(vo);
    }

    /** 当前用户短链概览：总数、总点击、有效数。 */
    @Override
    public Result<UserStatsVO> getUserStats() {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        UserStatsVO vo = new UserStatsVO();
        vo.setTotalLinks(shortLinkMapper.countByUser(userId));
        vo.setTotalClicks(shortLinkMapper.sumClicksByUser(userId));
        vo.setActiveLinks(shortLinkMapper.countActiveByUser(userId));
        return Result.success(vo);
    }

    /** 当前用户短链列表（按创建时间倒序）。 */
    @Override
    public Result<List<ShortLink>> listMyLinks() {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        List<ShortLink> links = shortLinkMapper.selectList(new LambdaQueryWrapper<ShortLink>()
                .eq(ShortLink::getUserId, userId)
                .orderByDesc(ShortLink::getCreateTime));
        return Result.success(links);
    }


    /** 分页查询短链点击明细（脱敏后的 IP/UA 解析结果）。 */
    @Override
    public Result<List<ClickLogVO>> pageClickLogs(String shortCode, int page, int size) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) return Result.error(ResultCode.UNAUTHORIZED);
        if (page < 1) page = 1;
        if (size <= 0 || size > 50) size = 20;

        ShortLink link = shortLinkMapper.selectOne(new LambdaQueryWrapper<ShortLink>()
                .eq(ShortLink::getShortCode, shortCode)
                .eq(ShortLink::getUserId, userId));
        if (link == null) return Result.error(ResultCode.SHORT_LINK_NOT_EXIST);

        List<ClickLog> rows = clickLogMapper.selectList(new LambdaQueryWrapper<ClickLog>()
                .eq(ClickLog::getShortCode, shortCode)
                .orderByDesc(ClickLog::getCreateTime)
                .last("LIMIT " + ((page - 1) * size) + "," + size));

        List<ClickLogVO> list = rows.stream().map(r -> {
            ClickLogVO vo = new ClickLogVO();
            vo.setIp(r.getIp());
            vo.setCountry(r.getCountry());
            vo.setProvince(r.getProvince());
            vo.setCity(r.getCity());
            vo.setBrowser(r.getBrowser());
            vo.setOs(r.getOs());
            vo.setCreateTime(r.getCreateTime());
            return vo;
        }).toList();
        return Result.success(list);
    }

    /**
     * 管理员封禁/解封。DB 更新后于事务提交再删缓存，
     * 配合缓存内 status 字段，关闭“删缓存→并发回源旧值”窗口。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> updateStatus(ShortLinkStatusDTO dto) {
        String shortCode = dto.getShortCode();
        Integer targetStatus = dto.getStatus();

        ShortLink link = shortLinkMapper.selectOne(new LambdaQueryWrapper<ShortLink>()
                .eq(ShortLink::getShortCode, shortCode));
        if (link == null) {
            return Result.error(ResultCode.SHORT_LINK_NOT_EXIST);
        }

        Integer currentStatus = link.getStatus() == null ? 0 : link.getStatus();
        if (currentStatus.equals(targetStatus)) {
            return Result.error(targetStatus == 1
                    ? ResultCode.LINK_ALREADY_BANNED
                    : ResultCode.LINK_ALREADY_ACTIVE);
        }

        shortLinkMapper.update(null, new LambdaUpdateWrapper<ShortLink>()
                .eq(ShortLink::getId, link.getId())
                .set(ShortLink::getStatus, targetStatus));

        deleteCacheAfterCommit(List.of(CACHE_KEY_PREFIX + shortCode));

        log.info("管理员操作短链状态，shortCode={}, status={}", shortCode, targetStatus);
        return Result.success(targetStatus == 1
                ? MessageConstants.ADMIN_BAN_LINK_SUCCESS
                : MessageConstants.ADMIN_UNBAN_LINK_SUCCESS, null);
    }

    private List<DailyClickVO> buildDailyTrend(String shortCode, int days) {
        List<DailyClickVO> list = new ArrayList<>(days);
        LocalDate today = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            String key = ClickStatisticsService.CLICK_STAT_PREFIX + shortCode + ":" + day;
            String value = stringRedisTemplate.opsForValue().get(key);
            list.add(new DailyClickVO(day.toString(), value == null ? 0L : Long.parseLong(value)));
        }
        return list;
    }

    // ========================= 内部工具 =========================

    /** 落库、写缓存并加入布隆过滤器；唯一键冲突视为短码已被占用。 */
    private Result<String> saveResultCode(String custom, String originalUrl, Long userId,
                                          String title, LocalDateTime validDate) {
        ShortLink link = ShortLink.builder()
                .originalUrl(originalUrl)
                .shortCode(custom)
                .userId(userId)
                .title(title)
                .clickNum(0L)
                .validDate(validDate)
                .build();
        try {
            shortLinkMapper.insert(link);
        } catch (DuplicateKeyException e) {
            log.warn(e.getMessage());
            return Result.error(ResultCode.SHORT_CODE_OCCUPIED);
        }

        writeCache(CACHE_KEY_PREFIX + custom, originalUrl, validDate, 0);
        bloomFilterService.addShortCode(custom);
        return Result.success(custom);
    }

    private boolean expired(LocalDateTime validDate) {
        return validDate != null && LocalDateTime.now().isAfter(validDate);
    }

    /** 读取 JSON 缓存；旧版纯字符串或损坏数据直接淘汰。 */
    private ShortLinkCacheVO readCache(String cacheKey) {
        String raw = stringRedisTemplate.opsForValue().get(cacheKey);
        if (raw == null || !raw.startsWith("{")) {
            if (raw != null) {
                stringRedisTemplate.delete(cacheKey);
            }
            return null;
        }
        try {
            return objectMapper.readValue(raw, ShortLinkCacheVO.class);
        } catch (Exception e) {
            stringRedisTemplate.delete(cacheKey);
            return null;
        }
    }

    /**
     * 写入含 status 的缓存；TTL = 剩余有效期 + [0,12h) 随机抖动，降低集中过期。
     */
    private void writeCache(String cacheKey, String url, LocalDateTime validDate, Integer status) {
        try {
            long ttl = Duration.between(LocalDateTime.now(),validDate).getSeconds();
            if  (ttl <= 0) {
                return;
            }
            long random = ThreadLocalRandom.current().nextLong(12 * 3600);
            int st = status == null ? 0 : status;
            String json = objectMapper.writeValueAsString(new ShortLinkCacheVO(url, validDate, st));
            stringRedisTemplate.opsForValue().set(cacheKey, json, ttl+random, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("write short-link cache failed: {}", cacheKey, e);
        }
    }

    /**
     * 仅在事务成功提交后删除 Redis 缓存。
     * 若在事务内提前删 key，并发请求可能 MVCC 读到旧行并写回过期缓存。
     */
    private void deleteCacheAfterCommit(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        List<String> keyList = List.copyOf(keys);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            stringRedisTemplate.delete(keyList);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                stringRedisTemplate.delete(keyList);
            }
        });
    }


    /** 系统保留短码（路由/管理语义），禁止用户自定义占用。 */
    private boolean checkCode(String code) {
        Set<String> reserved = Set.of("admin", "api", "login", "register", "auth",
                "stats", "link", "user", "manage", "system", "www", "mail", "ftp", "localhost");
        return reserved.contains(code.toLowerCase());
    }

    /** 预留：基于 MurmurHash 的短码生成（当前主路径使用雪花 ID + Base62）。 */
    private Long generateHashCode(String input) {
        return HashUtil.murmur64(input.getBytes(StandardCharsets.UTF_8));
    }
}