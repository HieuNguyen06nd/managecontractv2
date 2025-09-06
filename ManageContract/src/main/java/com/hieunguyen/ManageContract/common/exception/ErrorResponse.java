package com.hieunguyen.ManageContract.common.exception;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    private Date timestamp;    // Thời điểm xảy ra lỗi
    private int status;        // Mã HTTP (400, 404, 500, v.v.)
    private String error;      // Tên lỗi, ví dụ: "Bad Request"
    private String message;    // Thông báo lỗi chi tiết
    private String path;       // Đường dẫn API gây ra lỗi (nếu muốn hiển thị)
}