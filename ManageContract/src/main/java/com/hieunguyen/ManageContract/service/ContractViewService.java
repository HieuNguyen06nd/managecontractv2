package com.hieunguyen.ManageContract.service;

import com.hieunguyen.ManageContract.dto.file.FilePayload;
import com.hieunguyen.ManageContract.entity.Contract;
import com.hieunguyen.ManageContract.repository.ContractRepository;
import com.hieunguyen.ManageContract.security.jwt.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
@RequiredArgsConstructor
public class ContractViewService {

    private final ContractFileService contractFileService;
    private final ContractRepository contractRepository;
    private final SecurityUtil securityUtil;

    /**
     * View inline PDF - luôn trả về PDF
     */
    public FilePayload viewPdf(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        File pdf = getContractPdf(contractId);
        Resource res = new FileSystemResource(pdf);

        return new FilePayload(res,
                getPdfFileName(contract),
                MediaType.APPLICATION_PDF,
                pdf.length());
    }

    /**
     * Download file - luôn là PDF
     */
    public FilePayload downloadOriginal(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        File pdf = getContractPdf(contractId);
        Resource res = new FileSystemResource(pdf);

        return new FilePayload(res,
                getPdfFileName(contract),
                MediaType.APPLICATION_PDF,
                pdf.length());
    }

    /**
     * Lấy file PDF của contract
     */
    private File getContractPdf(Long contractId) {
        try {
            File pdfFile = contractFileService.getContractFile(contractId);
            // Kiểm tra file có tồn tại và có dữ liệu không
            if (pdfFile.exists() && pdfFile.length() > 0) {
                return pdfFile;
            } else {
                throw new RuntimeException("PDF file is empty or does not exist");
            }
        } catch (RuntimeException e) {
            // Nếu file chưa tồn tại, tạo mới
            Contract contract = contractRepository.findById(contractId)
                    .orElseThrow(() -> new RuntimeException("Contract not found"));

            // Tạo file PDF từ template
            String filePath = contractFileService.generateContractFile(contract);

            // Kiểm tra file mới tạo
            File newPdfFile = new File(filePath);
            if (newPdfFile.exists() && newPdfFile.length() > 0) {
                return newPdfFile;
            } else {
                throw new RuntimeException("Failed to generate PDF file");
            }
        }
    }

    /**
     * Tạo tên file PDF
     */
    private String getPdfFileName(Contract contract) {
        String baseName = "contract";
        if (contract.getContractNumber() != null && !contract.getContractNumber().isEmpty()) {
            baseName = contract.getContractNumber();
        } else if (contract.getTitle() != null && !contract.getTitle().isEmpty()) {
            baseName = contract.getTitle().replaceAll("[^a-zA-Z0-9\\-\\.]", "_");
        }
        return baseName + ".pdf";
    }
}