package com.hieunguyen.ManageContract.common.constants;

public enum ContractStatus {
    DRAFT,        // bản nháp
    PENDING_APPROVAL,    // đang gửi duyệt
    APPROVED,     // đã duyệt
    REJECTED,     // bị từ chối
    ACTIVE,       // có hiệu lực
    EXPIRED,      // hết hạn
    TERMINATED    // chấm dứt trước hạn
}

