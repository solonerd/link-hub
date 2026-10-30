package com.linkhub.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class AdminUserQueryDTO {

    @Min(value = 1, message = "页码最小为 1")
    private Integer page = 1;

    @Min(value = 1, message = "每页大小最小为 1")
    private Integer size = 20;

    private String keyword;        // 搜索关键词（用户名/邮箱）
    private Integer status;        // 状态筛选：0-正常, 1-封禁
}