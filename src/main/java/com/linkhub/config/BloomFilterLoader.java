package com.linkhub.config;

import com.linkhub.entity.ShortLink;
import com.linkhub.service.BloomFilterService;
import com.linkhub.service.ShortLinkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时按主键游标分批将已有短码灌入 Redis 布隆过滤器，
 * 避免冷启动后大量「假阴性」打穿到数据库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BloomFilterLoader implements CommandLineRunner {

    private final ShortLinkService shortLinkService;
    private final BloomFilterService bloomFilterService;
    private static final int batch_size = 1000;

    @Override
    public void run(String... args) {
        log.info("开始加载所有短码到布隆过滤器...");
        long lastId = 0;
        long total = 0;
        while (true) {
            List<ShortLink> list = shortLinkService.lambdaQuery()
                    .gt(ShortLink::getId,lastId)
                    .select(ShortLink::getId,ShortLink::getShortCode)
                    .orderByAsc(ShortLink::getId)
                    .last("limit "+ batch_size)
                    .list();
            if (list.isEmpty()) {
                break;
            }
            List<String> codes = list.stream().map(ShortLink::getShortCode).toList();
            bloomFilterService.addShortCodeBatch(codes);
            lastId = list.getLast().getId();
            total += list.size();
        }

        log.info("布隆过滤器加载完成，共 {} 条短码", total);
    }
}