package com.hieunguyen.ManageContract.repository;

import com.hieunguyen.ManageContract.entity.TemplateTableVariable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TemplateTableVariableRepository extends JpaRepository<TemplateTableVariable, Long> {
    List<TemplateTableVariable> findByTemplateId(Long templateId);
}