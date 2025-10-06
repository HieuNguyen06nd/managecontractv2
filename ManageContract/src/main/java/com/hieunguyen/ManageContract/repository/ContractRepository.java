package com.hieunguyen.ManageContract.repository;

import com.hieunguyen.ManageContract.common.constants.ContractStatus;
import com.hieunguyen.ManageContract.entity.Contract;
import com.hieunguyen.ManageContract.entity.ContractTemplate;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ContractRepository extends JpaRepository<Contract, Long> {
    @Query("SELECT c FROM ContractTemplate c WHERE c.id = :id")
    Optional<ContractTemplate> findTemplateById(@Param("id") Long id);

    // tất cả hợp đồng do email này tạo
    List<Contract> findByCreatedBy_Account_Email(String email);

    // lọc theo trạng thái (tùy chọn)
    List<Contract> findByCreatedBy_Account_EmailAndStatus(String email, ContractStatus status);
}
