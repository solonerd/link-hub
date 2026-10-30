package com.linkhub.service.impl;

import com.linkhub.entity.ShortLink;
import com.linkhub.service.BloomFilterService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBatch;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 基于 Redisson 的短码布隆过滤器：跳转路径上快速否定不存在短码。
 * 仅可能误判「存在」，不会误判「不存在」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BloomFilterServiceImpl implements BloomFilterService {

    private final RedissonClient redissonClient;

    private RBloomFilter<String> bloomFilter;

    private static final String FILTER_NAME = "shortCodeBloomFilter";
    private static final long EXPECTED_INSERTIONS = 1_000_000L;
    private static final double FALSE_PROBABILITY = 0.01;

    @PostConstruct
    public void init() {
        this.bloomFilter = redissonClient.getBloomFilter(FILTER_NAME);
        // tryInit 仅首次创建生效，已存在则保留原配置
        boolean initialized = this.bloomFilter.tryInit(EXPECTED_INSERTIONS, FALSE_PROBABILITY);
        if (initialized) {
            log.info("布隆过滤器初始化成功，预计容量: {}, 误判率: {}", EXPECTED_INSERTIONS, FALSE_PROBABILITY);
        } else {
            log.info("布隆过滤器已存在，加载已有配置");
        }
    }

    @Override
    public void addShortCode(String shortCode) {
        if (shortCode != null && !shortCode.isEmpty()) {
            bloomFilter.add(shortCode);
        }

    }

    @Override
    public void addShortCodeBatch(List<String> shortCodes) {
        if (shortCodes == null || shortCodes.isEmpty()) {
            return;
        }
        bloomFilter.add(shortCodes);
    }

    @Override
    public boolean mightContain(String shortCode) {
        if (shortCode == null || shortCode.isEmpty()) {
            return false;
        }
        return bloomFilter.contains(shortCode);
    }
}