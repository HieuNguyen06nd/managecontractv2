package com.hieunguyen.ManageContract.repository;

import com.hieunguyen.ManageContract.entity.ContractSignature;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractSignatureRepository extends JpaRepository<ContractSignature, Long> {
    boolean existsByApprovalStep_Id(Long approvalStepId);
}
