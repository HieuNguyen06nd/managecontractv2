package com.hieunguyen.ManageContract.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Data;

import java.util.List;

@Entity
@Table(name = "contract_templates")
@Data
public class ContractTemplate {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String description;
    private String filePath;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private User createdBy;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL)
    @JsonManagedReference
    private List<TemplateVariable> variables;
}

