package com.hieunguyen.ManageContract.dto.approval;

import lombok.Data;

@Data
public class ApprovalFlowRequest {
    private String name;
    private String description;
    private Long templateId; // gắn với contract template
}
