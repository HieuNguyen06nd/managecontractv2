package com.hieunguyen.ManageContract.entity;

import com.hieunguyen.ManageContract.common.constants.ApprovalStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "contract_approvals")
@Data
public class ContractApproval {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    private LocalDateTime approvedAt;
    private String comment;

    @ManyToOne @JoinColumn(name = "contract_id")
    private Contract contract;

    @ManyToOne @JoinColumn(name = "step_id")
    private ApprovalStep step;

    @ManyToOne @JoinColumn(name = "approver_id")
    private User approver;

    private Boolean isCurrent = true;
}

