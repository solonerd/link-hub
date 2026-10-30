package com.linkhub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailMessageDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 目标接收邮箱
     */
    private String email;

    /**
     * 验证码内容
     */
    private String code;

    /**
     * 业务场景（如：REGISTER, LOGIN, RESET_PASSWORD）
     * 以后如果你有多种邮件通知需求，这个字段能帮你做区分
     */
    private String scene;

    /**
     * 发送时间戳（用于 MQ 消费时进行逻辑判断）
     */
    private Long timestamp;
}