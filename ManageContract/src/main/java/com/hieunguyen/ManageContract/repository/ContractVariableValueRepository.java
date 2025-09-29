package com.hieunguyen.ManageContract.repository;

import com.hieunguyen.ManageContract.entity.ContractVariableValue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContractVariableValueRepository extends JpaRepository<ContractVariableValue, Long> {
    List<ContractVariableValue> findByContract_Id(Long contractId);
}
