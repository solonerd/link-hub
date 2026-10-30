package com.linkhub.vo;

import lombok.Data;

@Data
public class UserStatsVO {
    private Long totalLinks;
    private Long totalClicks;
    private Long activeLinks;
}