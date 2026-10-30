package com.linkhub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_click_log")
@Schema(description = "点击日志实体")
public class ClickLog {

    @Schema(description = "主键ID", example = "1782391028391028384")
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @Schema(description = "短链接码", example = "aX7kP2")
    /*
    default unique in design
    * */
    private String shortCode;

    @Schema(description = "客户端IP地址", example = "114.247.50.2")
    private String ip;

    @Schema(description = "国家", example = "中国")
    private String country;

    @Schema(description = "省份", example = "广东省")
    private String province;

    @Schema(description = "城市", example = "深圳市")
    private String city;

    @Schema(description = "浏览器名称及版本", example = "Chrome 120.0")
    private String browser;

    @Schema(description = "操作系统", example = "Windows 11")
    private String os;

    @Schema(description = "原始 User-Agent 请求头信息", example = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)...")
    private String userAgent;

    @Schema(description = "访问/创建时间", example = "2026-03-29 14:30:00")
    private LocalDateTime createTime;
}