package com.hieunguyen.ManageContract.repository;

import com.hieunguyen.ManageContract.entity.Contract;
import com.hieunguyen.ManageContract.entity.ContractTemplate;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ContractRepository extends JpaRepository<Contract, Long> {
    @Query("SELECT c FROM ContractTemplate c WHERE c.id = :id")
    Optional<ContractTemplate> findTemplateById(@Param("id") Long id);
}
