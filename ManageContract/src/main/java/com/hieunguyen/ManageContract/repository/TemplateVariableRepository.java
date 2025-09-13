package com.hieunguyen.ManageContract.repository;

import com.hieunguyen.ManageContract.entity.ContractTemplate;
import com.hieunguyen.ManageContract.entity.TemplateVariable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TemplateVariableRepository extends JpaRepository<TemplateVariable, Long> {
    Optional<TemplateVariable> findByTemplateAndVarName(ContractTemplate template, String varName);

    List<TemplateVariable> findByTemplate(ContractTemplate template);
}
