package com.hieunguyen.ManageContract.dto.contractSign;

import lombok.Data;

@Data
public class SignStepRequest {
    private String comment;
    private String imageBase64;   // "data:image/png;base64,..." hoặc chỉ base64
    private String placeholder;   // ưu tiên dùng, ví dụ ${SIGN_STEP_1}
    private Integer page;         // nếu không dùng placeholder
    private Float x;
    private Float y;
    private Float width;
    private Float height;
}
