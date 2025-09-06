package com.hieunguyen.ManageContract.common.constants;

public enum PaymentStatus {
    PENDING,     // chờ thanh toán
    PARTIAL,     // thanh toán một phần
    COMPLETED,   // hoàn tất
    FAILED,      // lỗi
    CANCELLED    // hủy
}

