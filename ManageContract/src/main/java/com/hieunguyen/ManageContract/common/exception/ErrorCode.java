package com.hieunguyen.ManageContract.common.exception;

public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized error"),
    BAD_REQUEST(400, "Bad request"),
    INVALID_KEY(1001, "Invalid key error"),
    USER_EXISTED(1002, "User existed"),
    USERNAME_INVALID(1003, "Username must be at least 3 characters"),
    INVALID_PASSWORD(1004, "Password must be at least 8 characters"),
    USER_NOT_EXISTED(1005, "User not existed"),
    UNAUTHENTICATED(1006, "Unauthenticated"),
    CATEGORY_EXISTED(1007, "Category existed"),
    NOT_FOUND(1008, "Not found"),
    FORBIDDEN(403, "Forbidden"),

    // Thêm các mã lỗi mới cho xử lý file
    INTERNAL_SERVER_ERROR(500, "Internal Server Error"),
    FILE_UPLOAD_FAILED(2001, "File upload failed"),
    FILE_NOT_FOUND(2002, "File not found"),
    FILE_DELETE_FAILED(2003, "File delete failed"),
    INVALID_FILE_TYPE(2004, "Invalid file type"),
    FILE_SIZE_EXCEEDED(2005, "File size exceeded limit"),
    INVALID_MODULE_NAME(2006, "Invalid module name"),
    FILE_EMPTY(2007, "File cannot be empty"),
    STORAGE_ERROR(2008, "File storage error"),
    FILE_READ_ERROR(2009, "Failed to read file"),
    FILE_WRITE_ERROR(2010, "Failed to write file"),
    INVALID_FILE_PATH(2011, "Invalid file path"),
    DUPLICATE_FILE(2012, "File already exists");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    // Helper method để tìm ErrorCode theo code
    public static ErrorCode fromCode(int code) {
        for (ErrorCode errorCode : values()) {
            if (errorCode.getCode() == code) {
                return errorCode;
            }
        }
        return UNCATEGORIZED_EXCEPTION;
    }
}
