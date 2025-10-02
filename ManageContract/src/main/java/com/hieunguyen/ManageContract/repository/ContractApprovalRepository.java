package com.hieunguyen.ManageContract.repository;

import com.hieunguyen.ManageContract.common.constants.ApprovalStatus;
import com.hieunguyen.ManageContract.common.constants.ContractStatus;
import com.hieunguyen.ManageContract.entity.Contract;
import com.hieunguyen.ManageContract.entity.ContractApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContractApprovalRepository extends JpaRepository<ContractApproval, Long> {
    List<ContractApproval> findByContractIdOrderByStepOrderAsc(Long contractId);

    Optional<ContractApproval> findByContractIdAndStepId(Long contractId, Long stepId);

    Optional<ContractApproval> findByContractIdAndStepOrder(Long contractId, Integer stepOrder);

    // Kiểm tra hợp đồng đã có step approval chưa
    boolean existsByContractId(Long contractId);

    @Query("SELECT ca.contract FROM ContractApproval ca " +
            "WHERE ca.approver.id = :userId AND ca.contract.status = :status")
    List<Contract> findAllByApproverIdAndContract_Status(@Param("userId") Long userId,
                                                         @Param("status") ContractStatus status);

    //  Thêm method mới có lọc cả status của contract
    List<ContractApproval> findAllByIsCurrentTrueAndStatusAndContract_Status(
            ApprovalStatus approvalStatus,
            ContractStatus contractStatus
    );

    Optional<ContractApproval> findByContractIdAndIsCurrentTrue(Long contractId);

}
