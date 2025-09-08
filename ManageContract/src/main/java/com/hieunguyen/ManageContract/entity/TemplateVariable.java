package com.hieunguyen.ManageContract.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.hieunguyen.ManageContract.common.constants.VariableType;
import jakarta.persistence.*;
import lombok.Data;

import java.util.List;

@Entity
@Table(name = "template_variables")
@Data
public class TemplateVariable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String varName;

    @Enumerated(EnumType.STRING)
    private VariableType varType;

    private Boolean required;

    private String defaultValue;

    @ElementCollection
    private List<String> allowedValues; // cho DROPDOWN / LIST

    @ManyToOne
    @JoinColumn(name = "template_id")
    @JsonBackReference
    private ContractTemplate template;
}
