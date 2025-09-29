package com.hieunguyen.ManageContract.common.constants;


public enum ApprovalAction {
    APPROVE_ONLY,      // Chỉ phê duyệt (như hiện tại)
    SIGN_ONLY,         // Chỉ ký (ký xong coi như hoàn thành step)
    SIGN_THEN_APPROVE  // Phải ký trước, sau đó mới bấm phê duyệt
}
