package com.hieunguyen.ManageContract.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "approval_steps")
@Data
public class ApprovalStep {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer stepOrder;
    private Boolean required;

    @ManyToOne @JoinColumn(name = "flow_id")
    private ApprovalFlow flow;

    @ManyToOne @JoinColumn(name = "role_id")
    private Role role;

    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department department;

    private Boolean isFinalStep = false;
}
