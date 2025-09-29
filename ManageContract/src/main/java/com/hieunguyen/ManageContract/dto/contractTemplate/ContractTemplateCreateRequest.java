package com.hieunguyen.ManageContract.dto.contractTemplate;

import com.hieunguyen.ManageContract.dto.templateVariable.TemplateVariableRequest;
import lombok.Data;

import java.util.List;

@Data
public class ContractTemplateCreateRequest {
    private String name;
    private String description;
    private String tempFileName;
    private List<TemplateVariableRequest> variables;
}
