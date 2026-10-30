package com.linkhub.common;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一的 API 响应结果封装类
 */
@Data
@NoArgsConstructor
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Integer code;
    private String message;
    private T data;
    private long timestamp = System.currentTimeMillis();

    /**
     * 内部全参构造，专门用来给下面的静态方法复用，极大精简代码
     */
    public Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    // ==========================================
    // 1. 成功相关方法
    // ==========================================

    // 默认成功 (无数据)
    public static <T> Result<T> success() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    // 带数据的成功
    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }


    // 自定义提示的成功 (保留了你的灵活性)
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), message, data);
    }

    // ==========================================
    // 2. 失败相关方法
    // ==========================================

    // 🌟 核心新增：直接传入 ResultCode 枚举报错 (以后业务里最常用这个)
    public static <T> Result<T> error(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    // 默认失败 (兜底的 500 系统错误)
    public static <T> Result<T> error(String message) {
        return new Result<>(ResultCode.INTERNAL_SERVER_ERROR.getCode(), message, null);
    }

    // 自定义状态码的失败 (用于兼容极特殊的报错)
    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message, null);
    }
}