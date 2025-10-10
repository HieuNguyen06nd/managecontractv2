package com.hieunguyen.ManageContract.service;

import com.hieunguyen.ManageContract.entity.Contract;
import com.hieunguyen.ManageContract.entity.ContractSignature;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public interface ContractFileService {

    String generateContractFile(Contract contract);

    File getContractFile(Long contractId);

    // Phương thức mới - chỉ cần placeholder
    String embedSignature(String filePath, String imageUrl, String placeholder);

    // Phương thức thêm text phê duyệt
    void addApprovalText(String filePath, String approvalText);
}