package com.hieunguyen.ManageContract.common.exception;

public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    // Constructor chỉ với ErrorCode
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    // Constructor với ErrorCode và custom message
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}