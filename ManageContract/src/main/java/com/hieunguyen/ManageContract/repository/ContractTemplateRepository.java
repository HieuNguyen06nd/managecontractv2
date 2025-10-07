package com.hieunguyen.ManageContract.repository;

import com.hieunguyen.ManageContract.entity.ContractTemplate;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ContractTemplateRepository extends JpaRepository<ContractTemplate, Long> {

    @Query("""
        select t
        from ContractTemplate t
        left join fetch t.defaultFlow df
        where t.id = :id
    """)
    Optional<ContractTemplate> findByIdWithDefaultFlow(@Param("id") Long id);
}
