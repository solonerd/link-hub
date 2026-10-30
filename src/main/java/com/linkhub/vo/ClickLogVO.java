package com.linkhub.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ClickLogVO {
    private String ip;
    private String country;
    private String province;
    private String city;
    private String browser;
    private String os;
    private LocalDateTime createTime;
}