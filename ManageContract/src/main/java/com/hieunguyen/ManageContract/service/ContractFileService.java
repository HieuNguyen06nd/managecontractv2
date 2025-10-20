package com.hieunguyen.ManageContract.service;

import com.hieunguyen.ManageContract.entity.Contract;
import com.hieunguyen.ManageContract.entity.ContractVariableValue;

import java.io.File;
import java.util.List;

public interface ContractFileService {
    String generateContractFile(Contract contract);
    String generateContractFileWithVariables(Contract contract, List<ContractVariableValue> variableValues);
    File getContractFile(Long contractId);
    String embedSignature(String filePath, String imageUrl, String placeholder);
    String embedSignatureForApproval(Long contractId, String imageUrl, Long approvalId);
    void addApprovalText(String filePath, String approvalText);
    List<String> getSignaturePlaceholders(Long contractId);
    boolean validatePlaceholdersInContract(Long contractId);

    // Tạo DOCX từ template + biến
    String generateDocxFile(Contract contract);
    // Lấy PDF nếu có, nếu chưa có thì convert từ DOCX
    File getPdfOrConvert(Long contractId);

    String embedSignatureByName(Long contractId, String imageRef, String printedName, Float widthPx, Float heightPx);

    default String embedSignatureByName(Long contractId, String imageRef, String printedName) {
        return embedSignatureByName(contractId, imageRef, printedName, null, null);
    }

}