package com.hieunguyen.ManageContract.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "contract_signatures")
@Data
public class ContractSignature {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String signatureImage;
    private LocalDateTime signedAt;

    @ManyToOne @JoinColumn(name = "contract_id")
    private Contract contract;

    @ManyToOne @JoinColumn(name = "signer_id")
    private User signer;
}
