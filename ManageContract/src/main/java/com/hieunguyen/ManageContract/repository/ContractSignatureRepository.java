package com.hieunguyen.ManageContract.repository;

import com.hieunguyen.ManageContract.entity.ContractSignature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ContractSignatureRepository extends JpaRepository<ContractSignature, Long> {
    boolean existsByApprovalStep_Id(Long approvalStepId);

    // chữ ký gần nhất TRƯỚC thời điểm 'before'
    Optional<ContractSignature> findTopByContract_IdAndSignedAtBeforeOrderBySignedAtDesc(Long contractId, LocalDateTime before);

    // fallback khi chưa có decidedAt
    Optional<ContractSignature> findTopByContract_IdOrderBySignedAtDesc(Long contractId);
}
