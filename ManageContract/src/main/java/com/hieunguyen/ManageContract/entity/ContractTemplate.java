package com.hieunguyen.ManageContract.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.hieunguyen.ManageContract.common.constants.Status;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Table(name = "contract_templates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractTemplate {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String description;
    private String filePath;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private Employee createdBy;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL)
    @JsonManagedReference
    private List<TemplateVariable> variables;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL)
    @JsonManagedReference
    private List<TemplateTableVariable> tableVariables;

    @ManyToOne
    @JoinColumn(name = "default_flow_id")
    private ApprovalFlow defaultFlow;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    private Boolean allowOverrideFlow = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status =Status.ACTIVE;

}

