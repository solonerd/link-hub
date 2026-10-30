package com.linkhub.service;

import com.linkhub.entity.ShortLink;

import java.util.List;

/** 短码布隆过滤器：加速否定查询，降低无效回源。 */
public interface BloomFilterService {

    void addShortCode(String shortCode);

    void addShortCodeBatch(List<String> shortCodes);

    /**
     * @return true 可能存在；false 肯定不存在
     */
    boolean mightContain(String shortCode);
}