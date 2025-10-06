package com.hieunguyen.ManageContract.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.hieunguyen.ManageContract.common.constants.ApprovalAction;
import com.hieunguyen.ManageContract.common.constants.ApproverType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "approval_steps")
@Getter
@Setter
@NoArgsConstructor
public class ApprovalStep {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer stepOrder;
    private Boolean required;

    @ManyToOne @JoinColumn(name = "flow_id")
    @JsonBackReference
    private ApprovalFlow flow;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApproverType approverType; // USER hoặc POSITION

    // Vị trí (chức vụ) cần ký
    @ManyToOne
    @JoinColumn(name = "position_id")
    private Position position;

    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalAction action = ApprovalAction.APPROVE_ONLY;

    @Column(length = 255)
    private String signaturePlaceholder;

    private Boolean isFinalStep = false;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ApprovalStep that)) return false;
        return id != null && id.equals(that.getId());
    }
    @Override
    public int hashCode() { return getClass().hashCode(); }

}
