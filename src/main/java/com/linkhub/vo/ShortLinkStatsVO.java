package com.linkhub.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "单条短链统计")
public class ShortLinkStatsVO {

    @Schema(description = "短码")
    private String shortCode;

    @Schema(description = "原始长链接")
    private String originalUrl;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "累计点击次数")
    private Long clickNum;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "过期时间")
    private LocalDateTime validDate;

    @Schema(description = "是否已过期")
    private Boolean expired;

    @Schema(description = "近 N 天点击趋势")
    private List<DailyClickVO> dailyTrend;

    @Schema(description = "点击量 Top 10 省份")
    private List<RegionStatVO> topRegions;
}