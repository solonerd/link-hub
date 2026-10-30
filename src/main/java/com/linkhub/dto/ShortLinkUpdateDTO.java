package com.linkhub.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

@Data
public class ShortLinkUpdateDTO {

    @NotNull(message = "短链ID不能为空")
    private Long id;

    @URL(message = "请输入有效的长链接 URL 地址")
    private String originalUrl;

    private String title;

    private String gid;

    // ========== 🔥 新增：有效期天数 ==========
    @Min(value = 1, message = "有效期天数最小为 1 天")
    @Max(value = 365, message = "有效期天数最大为 365 天")
    private Integer validDays;
}