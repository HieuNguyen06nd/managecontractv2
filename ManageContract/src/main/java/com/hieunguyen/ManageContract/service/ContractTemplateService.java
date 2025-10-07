package com.hieunguyen.ManageContract.service;

import com.hieunguyen.ManageContract.dto.approval.ApprovalFlowResponse;
import com.hieunguyen.ManageContract.dto.contractTemplate.ContractTemplateCreateRequest;
import com.hieunguyen.ManageContract.dto.contractTemplate.ContractTemplateResponse;
import com.hieunguyen.ManageContract.dto.contractTemplate.ContractTemplateUpdateRequest;
import com.hieunguyen.ManageContract.dto.templateVariable.TemplatePreviewResponse;
import com.hieunguyen.ManageContract.dto.templateVariable.VariableUpdateRequest;
import com.hieunguyen.ManageContract.entity.AuthAccount;
import com.hieunguyen.ManageContract.entity.ContractTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface ContractTemplateService {

    void updateVariableTypes(Long templateId, List<VariableUpdateRequest> requests);

    ContractTemplateResponse updateTemplate(Long id, ContractTemplateUpdateRequest request);

    // preview từ file upload (không lưu DB)
    TemplatePreviewResponse previewFromFile(MultipartFile file) throws IOException;

    // preview từ Google Docs link (không lưu DB)
    TemplatePreviewResponse previewFromGoogleDoc(String docLink) throws IOException;

    // finalize: lưu template + biến vào DB, sử dụng tempFileName từ preview
    ContractTemplateResponse finalizeTemplate(ContractTemplateCreateRequest request, Long accountId);

    List<ContractTemplateResponse> getAllTemplates();

    ApprovalFlowResponse getDefaultFlowByTemplate(Long templateId);

}
