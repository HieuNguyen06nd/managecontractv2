package com.hieunguyen.ManageContract.repository;

import com.hieunguyen.ManageContract.entity.ContractApproval;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContractApprovalRepository extends JpaRepository<ContractApproval, Long> {
    List<ContractApproval> findByContractIdOrderByStepOrderAsc(Long contractId);

    Optional<ContractApproval> findByContractIdAndStepId(Long contractId, Long stepId);

    Optional<ContractApproval> findByContractIdAndStepOrder(Long contractId, Integer stepOrder);

    // Kiểm tra hợp đồng đã có step approval chưa
    boolean existsByContractId(Long contractId);
}
