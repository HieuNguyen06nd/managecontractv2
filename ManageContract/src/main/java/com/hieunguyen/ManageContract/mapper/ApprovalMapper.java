package com.hieunguyen.ManageContract.mapper;

import com.hieunguyen.ManageContract.dto.approval.ApprovalStepResponse;
import com.hieunguyen.ManageContract.entity.*;
import com.hieunguyen.ManageContract.common.constants.ApproverType;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public final class ApprovalMapper {
    private ApprovalMapper() {}

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // Map từ bước cấu hình (dùng cho preview flow)
    public static ApprovalStepResponse toResponse(ApprovalStep step) {
        if (step == null) return null;
        ApprovalStepResponse.ApprovalStepResponseBuilder b = ApprovalStepResponse.builder()
                .id(step.getId())
                .stepOrder(step.getStepOrder())
                .required(Boolean.TRUE.equals(step.getRequired()))
                .approverType(step.getApproverType())
                .isFinalStep(Boolean.TRUE.equals(step.getIsFinalStep()))
                .action(step.getAction())
                .signaturePlaceholder(step.getSignaturePlaceholder());

        if (step.getEmployee() != null) {
            b.employeeId(step.getEmployee().getId())
                    .employeeName(nameOf(step.getEmployee()));
        }
        if (step.getPosition() != null) {
            b.positionId(step.getPosition().getId())
                    .positionName(step.getPosition().getName());
        }
        if (step.getDepartment() != null) {
            b.departmentId(step.getDepartment().getId())
                    .departmentName(step.getDepartment().getName());
        }
        return b.build();
    }

    // Map từ snapshot runtime (ContractApproval)
    public static ApprovalStepResponse fromApproval(ContractApproval ca) {
        if (ca == null) return null;
        ApprovalStep step = ca.getStep();

        // Ưu tiên snapshot (CA), fallback qua step cấu hình
        Integer stepOrder = ca.getStepOrder() != null ? ca.getStepOrder() : (step != null ? step.getStepOrder() : null);
        Boolean required  = ca.getRequired()   != null ? ca.getRequired()   : (step != null && Boolean.TRUE.equals(step.getRequired()));
        Boolean isFinal   = ca.getIsFinalStep()!= null ? ca.getIsFinalStep(): (step != null && Boolean.TRUE.equals(step.getIsFinalStep()));
        String  sigSlot   = ca.getSignaturePlaceholder() != null ? ca.getSignaturePlaceholder()
                : (step != null ? step.getSignaturePlaceholder() : null);

        ApproverType approverType = (step != null ? step.getApproverType()
                : (ca.getApprover() != null ? ApproverType.USER : ApproverType.POSITION));

        ApprovalStepResponse.ApprovalStepResponseBuilder b = ApprovalStepResponse.builder()
                .id(step != null ? step.getId() : null)
                .stepOrder(stepOrder)
                .required(Boolean.TRUE.equals(required))
                .isFinalStep(Boolean.TRUE.equals(isFinal))
                .approverType(approverType)
                .action(step != null ? step.getAction() : null)
                .signaturePlaceholder(sigSlot)
                .status(ca.getStatus())
                .isCurrent(Boolean.TRUE.equals(ca.getIsCurrent()))
                .decidedBy(ca.getApprover() != null ? nameOf(ca.getApprover()) : null)
                .decidedAt(format(ts(ca)));

        // Thực thể phụ trách (nếu snapshot là theo vị trí/phòng ban)
        if (ca.getApprover() != null) {
            b.employeeId(ca.getApprover().getId())
                    .employeeName(nameOf(ca.getApprover()));
        } else {
            if (ca.getPosition() != null) {
                b.positionId(ca.getPosition().getId())
                        .positionName(ca.getPosition().getName());
            }
            if (ca.getDepartment() != null) {
                b.departmentId(ca.getDepartment().getId())
                        .departmentName(ca.getDepartment().getName());
            }
        }
        return b.build();
    }

    // === helpers ===
    private static String nameOf(Employee e) {
        if (e == null) return null;
        String full = str(e.getFullName());
        if (!full.isEmpty()) return full;
        String n = str(e.getFullName());
        return n.isEmpty() ? "User#" + e.getId() : n;
    }

    private static String str(Object o) { return Objects.toString(o, "").trim(); }

    // Quy ước: thời điểm quyết định = approvedAt; nếu null thì updatedAt; nếu vẫn null -> null
    private static LocalDateTime ts(ContractApproval ca) {
        if (ca.getApprovedAt() != null) return ca.getApprovedAt();
        return ca.getUpdatedAt();
    }

    private static String format(LocalDateTime t) {
        return t == null ? null : ISO.format(t);
    }
}
