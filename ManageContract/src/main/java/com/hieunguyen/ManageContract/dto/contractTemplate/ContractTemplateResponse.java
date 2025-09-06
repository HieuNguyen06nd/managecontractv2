package com.hieunguyen.ManageContract.dto.contractTemplate;

import com.hieunguyen.ManageContract.dto.authAccount.AuthAccountResponse;
import com.hieunguyen.ManageContract.dto.templateVariable.TemplateVariableResponse;
import lombok.Data;

import java.util.List;

@Data
public class ContractTemplateResponse {
    private Long id;
    private String name;
    private String description;
    private String filePath;
    private AuthAccountResponse createdBy;
    private List<TemplateVariableResponse> variables;
}
