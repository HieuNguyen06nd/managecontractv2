package com.hieunguyen.ManageContract.entity;

import com.hieunguyen.ManageContract.common.constants.ContractStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.util.List;

@Entity
@Table(name = "contracts")
@Data
public class Contract {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String contractNumber;
    private String title;

    @Enumerated(EnumType.STRING)
    private ContractStatus status = ContractStatus.DRAFT;

    private String filePath;

    @ManyToOne @JoinColumn(name = "template_id")
    private ContractTemplate template;

    @ManyToOne @JoinColumn(name = "created_by")
    private AuthAccount createdBy;

    @OneToMany(mappedBy = "contract")
    private List<ContractVariableValue> variableValues;
}

