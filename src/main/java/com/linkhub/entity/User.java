package com.linkhub.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_user")
public class User {

    @TableId(type = IdType.ASSIGN_ID) // 雪花算法
    @TableField("user_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String username;

    private String password;

    private String email;

    private String phone;

    /**
     * 角色：USER-普通用户, ADMIN-管理员
     */
    private String role;

    /**
     * 状态：0-正常, 1-封禁
     */

    @TableField(fill = FieldFill.INSERT)
    private Integer status;

    /**
     * 逻辑删除：0-未删除, 1-已删除
     */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer delFlag;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}