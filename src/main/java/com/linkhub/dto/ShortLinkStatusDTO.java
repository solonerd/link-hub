package com.linkhub.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ShortLinkStatusDTO {

    @NotBlank(message = "短码不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_-]{4,16}$", message = "短码格式不正确")
    private String shortCode;

    /** 0-正常，1-封禁 */
    @NotNull(message = "status 不能为空")
    @Min(value = 0, message = "status 只能为 0 或 1")
    @Max(value = 1, message = "status 只能为 0 或 1")
    private Integer status;
}