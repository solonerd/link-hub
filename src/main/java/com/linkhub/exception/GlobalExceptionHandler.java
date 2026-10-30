package com.linkhub.exception;

import com.linkhub.common.Result;
import com.linkhub.common.ResultCode;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 1. 拦截 @Valid / @Validated 参数校验异常
     * 专门用于处理 Controller 层 DTO 校验失败的情况
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Object> handleValidationException(MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        // 提取 DTO 中定义的第一个错误提示信息（例如："密码不能为空"）
        String errorMessage = bindingResult.getAllErrors().getFirst().getDefaultMessage();

        // 校验失败属于客户端错误，记录 warn 级别日志即可，避免 error 日志过多
        log.warn("参数校验异常: {}", errorMessage);

        // 复用 Result 和 ResultCode 中的 BAD_REQUEST (400) 状态码
        return Result.error(ResultCode.BAD_REQUEST.getCode(), errorMessage);
    }

    /**
     * 2. 拦截 Spring Security 权限异常 (可选但推荐)
     * 避免因为没有权限被当做普通系统异常处理
     */
    @ExceptionHandler(AccessDeniedException.class)
    public Result<Object> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("权限拒绝异常: {}", e.getMessage());
        return Result.error(ResultCode.FORBIDDEN);
    }
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Object> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().iterator().next().getMessage();
        log.warn("参数校验异常: {}", msg);
        return Result.error(ResultCode.BAD_REQUEST.getCode(), msg);
    }

    @ExceptionHandler(RateLimitException.class)
    public Result<Object> handleRateLimitException(RateLimitException e) {
        log.warn("触发限流拦截: {}", e.getMessage());
        // 使用 ResultCode.TOO_MANY_REQUESTS 对应的 429
        return Result.error(e.getResultCode().getCode(), e.getMessage());
    }

    /**
     * 3. 拦截普通的业务运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public Result<Object> handleRuntimeException(RuntimeException e) {
        log.error("业务逻辑异常", e);
        // 保留你原有的逻辑，但建议状态码使用 500 或自定义的业务错误码
        return Result.error(ResultCode.INTERNAL_SERVER_ERROR.getCode(), e.getMessage());
    }

    /**
     * 4. 兜底处理所有未知的 Exception
     */
    @ExceptionHandler(Exception.class)
    public Result<Object> handleException(Exception e) {
        log.error("系统未知异常", e);
        // 直接使用封装好的 ResultCode.INTERNAL_SERVER_ERROR 枚举
        return Result.error(ResultCode.INTERNAL_SERVER_ERROR);
    }
}