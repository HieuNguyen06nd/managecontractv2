package com.hieunguyen.ManageContract.service.impl;

import com.hieunguyen.ManageContract.common.constants.ApprovalAction;
import com.hieunguyen.ManageContract.common.constants.ApproverType;
import com.hieunguyen.ManageContract.dto.approval.ApprovalFlowRequest;
import com.hieunguyen.ManageContract.dto.approval.ApprovalFlowResponse;
import com.hieunguyen.ManageContract.dto.approval.ApprovalStepRequest;
import com.hieunguyen.ManageContract.entity.*;
import com.hieunguyen.ManageContract.mapper.ApprovalFlowMapper;
import com.hieunguyen.ManageContract.repository.*;
import com.hieunguyen.ManageContract.service.ApprovalFlowService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApprovalFlowServiceImpl implements ApprovalFlowService {

    private final ApprovalFlowRepository flowRepository;
    private final ContractTemplateRepository templateRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final UserRepository userRepository;

    // ---------------- CREATE ----------------
    @Override
    @Transactional
    public ApprovalFlowResponse createFlow(ApprovalFlowRequest request) {
        ContractTemplate template = templateRepository.findById(request.getTemplateId())
                .orElseThrow(() -> new RuntimeException("Template not found"));

        ApprovalFlow flow = new ApprovalFlow();
        flow.setName(request.getName());
        flow.setDescription(request.getDescription());
        flow.setTemplate(template);

        // lưu trước để có id (tùy chiến lược, có thể không cần nhưng an toàn)
        flowRepository.save(flow);

        if (request.getSteps() == null || request.getSteps().isEmpty()) {
            throw new IllegalArgumentException("Flow phải có ít nhất 1 step");
        }
        List<ApprovalStepRequest> normalized = validateAndNormalizeSteps(request.getSteps());

        for (ApprovalStepRequest sr : normalized) {
            ApprovalStep step = mapToApprovalStepEntity(sr, flow);
            flow.addStep(step);
        }

        return ApprovalFlowMapper.toFlowResponse(flowRepository.save(flow));
    }
    // ---------------- UPDATE ----------------
    @Override
    @Transactional
    public ApprovalFlowResponse updateFlow(Long flowId, ApprovalFlowRequest request) {
        ApprovalFlow flow = flowRepository.findById(flowId)
                .orElseThrow(() -> new RuntimeException("Flow not found"));

        flow.setName(request.getName());
        flow.setDescription(request.getDescription());

        if (request.getSteps() == null || request.getSteps().isEmpty()) {
            throw new IllegalArgumentException("Flow phải có ít nhất 1 step");
        }
        List<ApprovalStepRequest> normalized = validateAndNormalizeSteps(request.getSteps());

        flow.getSteps().clear();

        for (ApprovalStepRequest sr : normalized) {
            ApprovalStep step = mapToApprovalStepEntity(sr, flow);
            flow.addStep(step);
        }

        return ApprovalFlowMapper.toFlowResponse(flowRepository.save(flow));
    }


    // ---------------- GET BY ID ----------------
    @Override
    public ApprovalFlowResponse getFlow(Long flowId) {
        ApprovalFlow flow = flowRepository.findById(flowId)
                .orElseThrow(() -> new RuntimeException("Flow not found"));
        return ApprovalFlowMapper.toFlowResponse(flow);
    }

    // ---------------- LIST BY TEMPLATE ----------------
    @Override
    public List<ApprovalFlowResponse> listFlowsByTemplate(Long templateId) {
        // kiểm tra tồn tại template cho rõ ràng
        templateRepository.findById(templateId)
                .orElseThrow(() -> new RuntimeException("Template not found"));
        var flows = flowRepository.findByTemplateId(templateId);
        return flows.stream().map(flow -> {
            ApprovalFlowResponse response = ApprovalFlowMapper.toFlowResponse(flow);
            response.setIsDefault(flow.getTemplate().getDefaultFlow() != null && flow.getTemplate().getDefaultFlow().getId().equals(flow.getId()));
            return response;
        }).collect(Collectors.toList());
    }

    // ---------------- SET DEFAULT ----------------
    @Override
    @Transactional
    public void setDefaultFlow(Long templateId, Long flowId) {
        ContractTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new RuntimeException("Template not found"));
        ApprovalFlow flow = flowRepository.findById(flowId)
                .orElseThrow(() -> new RuntimeException("Flow not found"));
        if (!Objects.equals(flow.getTemplate().getId(), template.getId())) {
            throw new IllegalArgumentException("Flow không thuộc template này");
        }
        template.setDefaultFlow(flow);
        templateRepository.save(template);
    }

    @Override
    @Transactional
    public void deleteFlow(Long flowId) {
        var flow = flowRepository.findById(flowId)
                .orElseThrow(() -> new RuntimeException("Flow not found"));
        // thêm cấm xoá nếu đang là default của template hoặc đã gắn với hợp đồng
        flowRepository.delete(flow);
    }

    // ---------------- Helpers ----------------

    /**
     * Chuẩn hoá & validate danh sách step:
     * - Nhận Collection để hỗ trợ cả Set/List
     * - stepOrder duy nhất, >=1, liên tục từ 1
     * - Nếu chưa có final → đặt bước cuối là final
     * - Chỉ hỗ trợ USER hoặc POSITION (POSITION bắt buộc departmentId + positionId)
     */
    private List<ApprovalStepRequest> validateAndNormalizeSteps(Collection<ApprovalStepRequest> steps) {
        var sorted = steps.stream()
                .sorted(Comparator.comparingInt(ApprovalStepRequest::getStepOrder))
                .toList();

        Set<Integer> seen = new HashSet<>();
        Set<String> seenPlaceholders = new HashSet<>();
        int expected = 1;
        boolean hasFinal = false;

        for (var s : sorted) {
            if (s.getStepOrder() == null || s.getStepOrder() < 1)
                throw new IllegalArgumentException("stepOrder phải >= 1");

            if (!seen.add(s.getStepOrder()))
                throw new IllegalArgumentException("stepOrder bị trùng: " + s.getStepOrder());

            if (s.getStepOrder() != expected)
                throw new IllegalArgumentException("stepOrder phải liên tục bắt đầu từ 1 (thiếu " + expected + ")");

            if (Boolean.TRUE.equals(s.getIsFinalStep())) hasFinal = true;

            if (s.getApproverType() == null)
                throw new IllegalArgumentException("approverType bắt buộc");

            if (s.getAction() == null)
                throw new IllegalArgumentException("action bắt buộc (APPROVE_ONLY | SIGN_ONLY | SIGN_THEN_APPROVE)");

            boolean requiresSign = (s.getAction() == ApprovalAction.SIGN_ONLY
                    || s.getAction() == ApprovalAction.SIGN_THEN_APPROVE);

            if (s.getApproverType() == ApproverType.USER) {
                if (s.getEmployeeId() == null)
                    throw new IllegalArgumentException("employeeId bắt buộc khi approverType=USER");
            } else if (s.getApproverType() == ApproverType.POSITION) {
                if (s.getPositionId() == null || s.getDepartmentId() == null)
                    throw new IllegalArgumentException("positionId và departmentId bắt buộc khi approverType=POSITION");
            } else {
                throw new IllegalArgumentException("approverType không hỗ trợ: " + s.getApproverType());
            }

            // Placeholder bắt buộc khi là bước ký + không trùng
            if (requiresSign) {
                if (s.getSignaturePlaceholder() == null || s.getSignaturePlaceholder().isBlank())
                    throw new IllegalArgumentException("signaturePlaceholder bắt buộc cho bước ký");
                if (!seenPlaceholders.add(s.getSignaturePlaceholder()))
                    throw new IllegalArgumentException("signaturePlaceholder bị trùng: " + s.getSignaturePlaceholder());
            }

            expected++;
        }

        if (!hasFinal && !sorted.isEmpty()) {
            List<ApprovalStepRequest> mutable = new ArrayList<>(sorted);
            mutable.get(mutable.size() - 1).setIsFinalStep(true);
            return mutable;
        }

        return new ArrayList<>(sorted);
    }

    private ApprovalStep mapToApprovalStepEntity(ApprovalStepRequest req, ApprovalFlow flow) {
        ApprovalStep step = new ApprovalStep();
        step.setFlow(flow);
        step.setStepOrder(req.getStepOrder());
        step.setRequired(Boolean.TRUE.equals(req.getRequired()));
        step.setIsFinalStep(Boolean.TRUE.equals(req.getIsFinalStep()));
        step.setApproverType(req.getApproverType());
        step.setAction(req.getAction());
        step.setSignaturePlaceholder(req.getSignaturePlaceholder()); // <-- CHUẨN

        if (req.getApproverType() == ApproverType.USER) {
            Employee emp = userRepository.findById(req.getEmployeeId())
                    .orElseThrow(() -> new RuntimeException("Employee not found"));
            step.setEmployee(emp);
            step.setDepartment(null);
            step.setPosition(null);
        } else {
            Position pos = positionRepository.findById(req.getPositionId())
                    .orElseThrow(() -> new RuntimeException("Position not found"));
            Department dep = departmentRepository.findById(req.getDepartmentId())
                    .orElseThrow(() -> new RuntimeException("Department not found"));
            step.setPosition(pos);
            step.setDepartment(dep);
            step.setEmployee(null);
        }

        return step;
    }

}
