package com.hieunguyen.ManageContract.mapper;

import com.hieunguyen.ManageContract.dto.approval.ApprovalFlowResponse;
import com.hieunguyen.ManageContract.dto.approval.ApprovalStepResponse;
import com.hieunguyen.ManageContract.entity.ApprovalFlow;
import com.hieunguyen.ManageContract.entity.ApprovalStep;

import java.util.stream.Collectors;

public class ApprovalFlowMapper {

    public static ApprovalFlowResponse toFlowResponse(ApprovalFlow flow) {
        ApprovalFlowResponse dto = new ApprovalFlowResponse();
        dto.setId(flow.getId());
        dto.setName(flow.getName());
        dto.setDescription(flow.getDescription());
        dto.setTemplateId(flow.getTemplate().getId());

        if (flow.getSteps() != null) {
            dto.setSteps(flow.getSteps()
                    .stream()
                    .map(ApprovalFlowMapper::toStepResponse)
                    .collect(Collectors.toList()));
        }
        return dto;
    }

    public static ApprovalStepResponse toStepResponse(ApprovalStep step) {
        ApprovalStepResponse dto = new ApprovalStepResponse();
        dto.setId(step.getId());
        dto.setStepOrder(step.getStepOrder());
        dto.setRequired(step.getRequired());
        dto.setIsFinalStep(step.getIsFinalStep());

        dto.setPositionId(step.getPosition() != null ? step.getPosition().getId() : null);
        dto.setDepartmentId(step.getDepartment() != null ? step.getDepartment().getId() : null);

        return dto;
    }
}
