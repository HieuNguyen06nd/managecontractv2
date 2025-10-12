package com.hieunguyen.ManageContract.entity;

import com.hieunguyen.ManageContract.common.constants.ContractStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;
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

    @ManyToOne
    @JoinColumn(name = "created_by", nullable = false)
    private Employee createdBy;

    @ManyToOne
    @JoinColumn(name = "flow_id")
    private ApprovalFlow flow;

    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL)
    private List<ContractVariableValue> variableValues;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "file_generated_at")
    private LocalDateTime fileGeneratedAt;
}

