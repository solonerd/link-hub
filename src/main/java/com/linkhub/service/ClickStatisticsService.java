package com.linkhub.service;

public interface ClickStatisticsService {
    String CLICK_STAT_PREFIX = "stat:click:daily:";

    void recordClick(String shortCode, String ip, String userAgent);
}