package com.hieunguyen.ManageContract.service.impl;

import com.hieunguyen.ManageContract.dto.approval.*;
import com.hieunguyen.ManageContract.entity.*;
import com.hieunguyen.ManageContract.repository.*;
import com.hieunguyen.ManageContract.service.ApprovalFlowService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApprovalFlowServiceImpl implements ApprovalFlowService {

    private final ApprovalFlowRepository flowRepository;
    private final ApprovalStepRepository stepRepository;
    private final ContractTemplateRepository templateRepository;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;

    @Override
    @Transactional
    public ApprovalFlowResponse createFlow(ApprovalFlowRequest request) {
        ContractTemplate template = templateRepository.findById(request.getTemplateId())
                .orElseThrow(() -> new RuntimeException("Template not found"));

        ApprovalFlow flow = new ApprovalFlow();
        flow.setName(request.getName());
        flow.setDescription(request.getDescription());
        flow.setTemplate(template);

        flow = flowRepository.save(flow);

        return toFlowResponse(flow);
    }

    @Override
    @Transactional
    public ApprovalStepResponse addStep(Long flowId, ApprovalStepRequest request) {
        ApprovalFlow flow = flowRepository.findById(flowId)
                .orElseThrow(() -> new RuntimeException("Flow not found"));

        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new RuntimeException("Role not found"));

        Department department = null;
        if (request.getDepartmentId() != null) {
            department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new RuntimeException("Department not found"));
        }

        ApprovalStep step = new ApprovalStep();
        step.setStepOrder(request.getStepOrder());
        step.setRequired(request.getRequired());

        step.setDepartment(department);
        step.setFlow(flow);
        step.setIsFinalStep(request.getIsFinalStep() != null && request.getIsFinalStep());

        step = stepRepository.save(step);

        return toStepResponse(step);
    }

    @Override
    public ApprovalFlowResponse getFlow(Long flowId) {
        ApprovalFlow flow = flowRepository.findById(flowId)
                .orElseThrow(() -> new RuntimeException("Flow not found"));
        return toFlowResponse(flow);
    }

    // --- Mapper ---
    private ApprovalFlowResponse toFlowResponse(ApprovalFlow flow) {
        ApprovalFlowResponse dto = new ApprovalFlowResponse();
        dto.setId(flow.getId());
        dto.setName(flow.getName());
        dto.setDescription(flow.getDescription());
        dto.setTemplateId(flow.getTemplate().getId());
        if (flow.getSteps() != null) {
            dto.setSteps(flow.getSteps().stream().map(this::toStepResponse).collect(Collectors.toList()));
        }
        return dto;
    }

    private ApprovalStepResponse toStepResponse(ApprovalStep step) {
        ApprovalStepResponse dto = new ApprovalStepResponse();
        dto.setId(step.getId());
        dto.setStepOrder(step.getStepOrder());
        dto.setRequired(step.getRequired());

        dto.setDepartmentId(step.getDepartment() != null ? step.getDepartment().getId() : null);
        dto.setIsFinalStep(step.getIsFinalStep());
        return dto;
    }
}
