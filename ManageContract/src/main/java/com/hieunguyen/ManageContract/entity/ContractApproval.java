package com.hieunguyen.ManageContract.entity;

import com.hieunguyen.ManageContract.common.constants.ApprovalStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "contract_approvals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Trạng thái step: PENDING, APPROVED, REJECTED
    @Enumerated(EnumType.STRING)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    // Thời gian approve step
    private LocalDateTime approvedAt;

    // Comment của người approve
    @Column(length = 1000)
    private String comment;

    // Liên kết hợp đồng
    @ManyToOne
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    // Liên kết step trong flow
    @ManyToOne
    @JoinColumn(name = "step_id", nullable = false)
    private ApprovalStep step;

    // User được gán approve cụ thể (nullable)
    @ManyToOne
    @JoinColumn(name = "approver_id")
    private User approver;

    // Step hiện tại đang chờ approve
    private Boolean isCurrent;

    // Thứ tự step trong flow
    private Integer stepOrder;

    // Step có bắt buộc approve hay không
    private Boolean required;

    // Step cuối cùng trong flow
    private Boolean isFinalStep;

    // Role của người approve nếu approver chưa gán
    @ManyToOne
    @JoinColumn(name = "role_id")
    private Role role;

    // Department của người approve nếu approver chưa gán
    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department department;

}
