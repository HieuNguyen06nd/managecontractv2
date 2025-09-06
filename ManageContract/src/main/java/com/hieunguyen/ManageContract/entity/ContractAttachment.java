package com.hieunguyen.ManageContract.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "contract_attachments")
@Data
public class ContractAttachment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fileName;
    private String filePath;

    @ManyToOne
    @JoinColumn(name = "contract_id")
    private Contract contract;
}

