package com.linkhub.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

@Data
public class ShortLinkCreateDTO {

    @NotBlank(message = "原始长链接不能为空")
    @URL(message = "请输入有效的长链接 URL 地址")
    private String originalUrl;

    private String title;

    private String gid;

    @Pattern(
            regexp = "^$|^[a-zA-Z0-9_-]{4,16}$",
            message = "自定义短码仅支持字母、数字、下划线、短横线，长度 4-16 位"
    )
    private String customCode;
    // ========== 🔥 新增：有效期天数 ==========
    @Min(value = 1, message = "有效期天数最小为 1 天")
    @Max(value = 90, message = "有效期天数最大为 90 天")
    private Integer validDays;
}