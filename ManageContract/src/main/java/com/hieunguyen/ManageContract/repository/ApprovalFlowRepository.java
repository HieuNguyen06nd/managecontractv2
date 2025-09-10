package com.hieunguyen.ManageContract.repository;

import com.hieunguyen.ManageContract.entity.ApprovalFlow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApprovalFlowRepository extends JpaRepository<ApprovalFlow, Long> {
    Optional<ApprovalFlow> findByTemplateId(Long templateId);
}
