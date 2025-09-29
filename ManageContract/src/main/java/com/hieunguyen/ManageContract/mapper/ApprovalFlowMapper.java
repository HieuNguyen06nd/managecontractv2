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
        return ApprovalStepResponse.builder()
                .id(step.getId())
                .stepOrder(step.getStepOrder())
                .required(step.getRequired())
                .approverType(step.getApproverType())
                .isFinalStep(step.getIsFinalStep())
                .employeeId(step.getEmployee() != null ? step.getEmployee().getId() : null)
                .employeeName(step.getEmployee() != null ? step.getEmployee().getFullName() : null)
                .positionId(step.getPosition() != null ? step.getPosition().getId() : null)
                .positionName(step.getPosition() != null ? step.getPosition().getName() : null)
                .departmentId(step.getDepartment() != null ? step.getDepartment().getId() : null)
                .departmentName(step.getDepartment() != null ? step.getDepartment().getName() : null)
                .build();
    }
}
