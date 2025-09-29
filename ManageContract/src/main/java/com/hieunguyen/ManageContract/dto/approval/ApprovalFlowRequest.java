package com.hieunguyen.ManageContract.dto.approval;

import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
public class ApprovalFlowRequest {
    private String name;
    private String description;
    private Long templateId;

    private Set<ApprovalStepRequest> steps;
}