package com.hieunguyen.ManageContract.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception ném khi cố tạo hoặc cập nhật resource bị trùng key (ví dụ mã discount đã tồn tại).
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException() {
        super();
    }
    public DuplicateResourceException(String message) {
        super(message);
    }
    public DuplicateResourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
