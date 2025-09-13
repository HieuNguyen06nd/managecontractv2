package com.hieunguyen.ManageContract.service;

import com.hieunguyen.ManageContract.dto.contractTemplate.ContractTemplateResponse;
import com.hieunguyen.ManageContract.dto.templateVariable.VariableUpdateRequest;
import com.hieunguyen.ManageContract.entity.AuthAccount;
import com.hieunguyen.ManageContract.entity.ContractTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface ContractTemplateService {
    ContractTemplateResponse uploadTemplate(MultipartFile file,Long accountId)throws IOException;

    ContractTemplateResponse uploadTemplateFromGoogleDoc(String docLink, Long accountId) throws IOException;

    void updateVariableTypes(Long templateId, List<VariableUpdateRequest> requests);
}
