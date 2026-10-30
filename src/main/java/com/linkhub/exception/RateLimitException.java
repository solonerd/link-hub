package com.linkhub.exception;

import com.linkhub.common.ResultCode;
import lombok.Getter;

@Getter
public class RateLimitException extends RuntimeException {

    private final ResultCode resultCode;

    public RateLimitException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }

    public RateLimitException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }
}