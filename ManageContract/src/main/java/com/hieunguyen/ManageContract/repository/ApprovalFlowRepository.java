package com.hieunguyen.ManageContract.repository;

import com.hieunguyen.ManageContract.entity.ApprovalFlow;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ApprovalFlowRepository extends JpaRepository<ApprovalFlow, Long> {
    List<ApprovalFlow> findByTemplateId(Long templateId);

    @Query("select t.defaultFlow from ContractTemplate t where t.id = :templateId")
    Optional<ApprovalFlow> findDefaultFlowByTemplateId(@Param("templateId") Long templateId);

    @Query("""
        select distinct f
        from ApprovalFlow f
        left join fetch f.steps s
        left join fetch s.employee e
        left join fetch s.position p
        left join fetch s.department d
        where f.id = :id
    """)
    Optional<ApprovalFlow> findByIdWithSteps(@Param("id") Long id);
}
