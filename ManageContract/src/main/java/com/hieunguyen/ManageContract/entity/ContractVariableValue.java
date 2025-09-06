package com.hieunguyen.ManageContract.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "contract_variable_values")
@Data
public class ContractVariableValue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String varName;
    private String varValue;

    @ManyToOne
    @JoinColumn(name = "contract_id")
    private Contract contract;
}

