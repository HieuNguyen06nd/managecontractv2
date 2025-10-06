package com.hieunguyen.ManageContract.dto.approval;

import lombok.Data;
import java.util.List;

@Data
public class ApprovalFlowResponse {
    private Long id;
    private String name;
    private String description;
    private Long templateId;
    private List<ApprovalStepResponse> steps;

    private Boolean isDefault;
}
