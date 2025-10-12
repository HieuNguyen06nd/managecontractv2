package com.hieunguyen.ManageContract.service;

import com.hieunguyen.ManageContract.dto.file.FilePayload;
import com.hieunguyen.ManageContract.entity.Contract;
import com.hieunguyen.ManageContract.entity.ContractVariableValue;
import com.hieunguyen.ManageContract.repository.ContractRepository;
import com.hieunguyen.ManageContract.repository.ContractVariableValueRepository;
import com.hieunguyen.ManageContract.security.jwt.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContractViewService {

    private final ContractFileService contractFileService;
    private final ContractRepository contractRepository;
    private final ContractVariableValueRepository variableValueRepository;
    private final SecurityUtil securityUtil;

    /**
     * View inline PDF - luôn trả về PDF với giá trị biến đã được fill
     */
    @Transactional
    public FilePayload viewPdf(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        // Lấy danh sách biến của hợp đồng
        List<ContractVariableValue> variableValues = variableValueRepository.findByContract_Id(contractId);

        // Tạo hoặc lấy file PDF với giá trị biến đã được fill
        File pdf = getContractPdfWithVariables(contract, variableValues);
        Resource res = new FileSystemResource(pdf);

        return new FilePayload(res,
                getPdfFileName(contract),
                MediaType.APPLICATION_PDF,
                pdf.length());
    }

    /**
     * Download file - luôn là PDF với giá trị biến đã được fill
     */
    @Transactional
    public FilePayload downloadOriginal(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        // Lấy danh sách biến của hợp đồng
        List<ContractVariableValue> variableValues = variableValueRepository.findByContract_Id(contractId);

        // Tạo hoặc lấy file PDF với giá trị biến đã được fill
        File pdf = getContractPdfWithVariables(contract, variableValues);
        Resource res = new FileSystemResource(pdf);

        return new FilePayload(res,
                getPdfFileName(contract),
                MediaType.APPLICATION_PDF,
                pdf.length());
    }

    /**
     * Lấy file PDF của contract với giá trị biến đã được fill
     */
    private File getContractPdfWithVariables(Contract contract, List<ContractVariableValue> variableValues) {
        try {
            // Thử lấy file PDF hiện tại
            File pdfFile = contractFileService.getContractFile(contract.getId());

            // Kiểm tra file có tồn tại và có dữ liệu không
            if (pdfFile.exists() && pdfFile.length() > 0 && !shouldRegeneratePdf(contract)) {
                return pdfFile;
            } else {
                // Nếu file không tồn tại hoặc cần tạo lại, tạo file mới với giá trị biến
                String filePath = contractFileService.generateContractFileWithVariables(contract, variableValues);

                // Kiểm tra file mới tạo
                File newPdfFile = new File(filePath);
                if (newPdfFile.exists() && newPdfFile.length() > 0) {
                    return newPdfFile;
                } else {
                    throw new RuntimeException("Failed to generate PDF file with variables");
                }
            }
        } catch (RuntimeException e) {
            // Nếu file chưa tồn tại, tạo mới với giá trị biến
            String filePath = contractFileService.generateContractFileWithVariables(contract, variableValues);

            File newPdfFile = new File(filePath);
            if (newPdfFile.exists() && newPdfFile.length() > 0) {
                return newPdfFile;
            } else {
                throw new RuntimeException("Failed to generate PDF file with variables: " + e.getMessage());
            }
        }
    }

    /**
     * Kiểm tra xem có cần tạo lại PDF không
     */
    private boolean shouldRegeneratePdf(Contract contract) {
        // Tạo lại PDF nếu:
        // 1. Hợp đồng ở trạng thái DRAFT (có thể đã thay đổi biến)
        // 2. File PDF chưa được tạo với giá trị biến mới nhất
        return contract.getStatus().toString().equals("DRAFT") ||
                contract.getUpdatedAt() != null;
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