package com.hieunguyen.ManageContract.entity;

import com.hieunguyen.ManageContract.common.constants.SignatureType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "contract_signatures")
@Data
public class ContractSignature {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String signatureImage;       // path file ảnh (PNG)
    private String imageMime;            // "image/png" (tuỳ chọn)
    private LocalDateTime signedAt;

    // Cách chèn:
    private String placeholderKey;       // ví dụ ${SIGN_STEP_1} (nếu dùng placeholder)
    private Integer page;                // nếu overlay PDF
    private Float x;
    private Float y;
    private Float width;
    private Float height;

    @Enumerated(EnumType.STRING)
    private SignatureType type;          // DRAWN / IMAGE /CKS

    @ManyToOne @JoinColumn(name = "contract_id")
    private Contract contract;

    @ManyToOne @JoinColumn(name = "signer_id")
    private Employee signer;

    @ManyToOne @JoinColumn(name = "approval_id")
    private ContractApproval approvalStep;
}

