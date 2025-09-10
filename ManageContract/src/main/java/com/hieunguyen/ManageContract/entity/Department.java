package com.hieunguyen.ManageContract.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "departments")
@Data
public class Department {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    // Quan hệ với ContractApproval (nếu step chưa gán approver)
    @OneToMany(mappedBy = "department")
    private Set<ContractApproval> approvals = new HashSet<>();
}

