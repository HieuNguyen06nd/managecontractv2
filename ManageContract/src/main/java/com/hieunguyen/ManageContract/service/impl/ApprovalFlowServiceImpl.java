package com.hieunguyen.ManageContract.service.impl;

import com.hieunguyen.ManageContract.dto.approval.*;
import com.hieunguyen.ManageContract.entity.*;
import com.hieunguyen.ManageContract.mapper.ApprovalFlowMapper;
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
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;

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

        return ApprovalFlowMapper.toFlowResponse(flow);
    }

    @Override
    @Transactional
    public ApprovalStepResponse addStep(Long flowId, ApprovalStepRequest request) {
        ApprovalFlow flow = flowRepository.findById(flowId)
                .orElseThrow(() -> new RuntimeException("Flow not found"));

        Department department = null;
        if (request.getDepartmentId() != null) {
            department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new RuntimeException("Department not found"));
        }

        Position position = positionRepository.findById(request.getPositionId())
                .orElseThrow(() -> new RuntimeException("Position not found"));

        ApprovalStep step = new ApprovalStep();
        step.setStepOrder(request.getStepOrder());
        step.setRequired(request.getRequired());
        step.setFlow(flow);
        step.setDepartment(department);
        step.setPosition(position);
        step.setIsFinalStep(request.getIsFinalStep() != null && request.getIsFinalStep());

        step = stepRepository.save(step);

        return ApprovalFlowMapper.toStepResponse(step);
    }

    @Override
    public ApprovalFlowResponse getFlow(Long flowId) {
        ApprovalFlow flow = flowRepository.findById(flowId)
                .orElseThrow(() -> new RuntimeException("Flow not found"));
        return ApprovalFlowMapper.toFlowResponse(flow);
    }

}

