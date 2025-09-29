package com.hieunguyen.ManageContract.repository;

import com.hieunguyen.ManageContract.entity.ApprovalFlow;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ApprovalFlowRepository extends JpaRepository<ApprovalFlow, Long> {
    // trả về nhiều flow
    List<ApprovalFlow> findByTemplateId(Long templateId);

    // lấy đúng 1 flow mặc định (nếu bạn set mặc định trên Template)
    @Query("select t.defaultFlow from ContractTemplate t where t.id = :templateId")
    Optional<ApprovalFlow> findDefaultFlowByTemplateId(@Param("templateId") Long templateId);
}
