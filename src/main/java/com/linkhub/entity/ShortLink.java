package com.linkhub.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_link")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortLink implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID (雪花算法)
     */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String originalUrl;

    private String shortCode;

    private String title;

    private String gid;

    /**
     * 用户 ID (外键，关联 t_user 表的 user_id)
     * 💡 加上 @TableField 指定数据库字段名，防止驼峰和下划线映射冲突
     */
    @TableField("user_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private Long clickNum;

    @TableField(fill = FieldFill.INSERT)
    private Integer status;

    private LocalDateTime validDate;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer delFlag;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}