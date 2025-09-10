package com.hieunguyen.ManageContract.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.util.List;

@Entity
@Table(name = "approval_flows")
@Data
public class ApprovalFlow {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String description;

    private Boolean allowCustomFlow = true; // mặc định cho sửa

    @ManyToOne @JoinColumn(name = "template_id")
    private ContractTemplate template;

    @OneToMany(mappedBy = "flow")
    private List<ApprovalStep> steps;
}

