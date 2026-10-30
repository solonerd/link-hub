package com.linkhub.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 短链 Redis 缓存载荷。
 * 必须包含 {@code status}，否则封禁后缓存命中仍可能返回原链。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShortLinkCacheVO {
    private String url;
    private LocalDateTime validDate;
    /** 0=正常；非 0=封禁/不可用 */
    private Integer status;
}
