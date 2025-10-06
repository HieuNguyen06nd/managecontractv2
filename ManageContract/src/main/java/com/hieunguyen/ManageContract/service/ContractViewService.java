package com.hieunguyen.ManageContract.service;

import com.hieunguyen.ManageContract.dto.file.FilePayload;
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
    private final SecurityUtil securityUtil; // nếu cần check quyền

    /**
     * View inline PDF.
     * - Nếu hợp đồng mới có DOCX, sẽ convert sang PDF rồi trả inline.
     */
    public FilePayload viewPdf(Long contractId) {
        File pdf = contractFileService.getPdfOrConvert(contractId);

        Resource res = new FileSystemResource(pdf);
        return new FilePayload(res, pdf.getName(), MediaType.APPLICATION_PDF, pdf.length());
    }

    /**
     * Download file gốc (DOCX nếu còn nháp, PDF nếu đã có).
     */
    public FilePayload downloadOriginal(Long contractId) {
        File file = contractFileService.getContractFile(contractId);

        MediaType mediaType = resolveMediaType(file.getName());
        Resource res = new FileSystemResource(file);
        return new FilePayload(res, file.getName(), mediaType, file.length());
    }

    // --------- helpers ---------

    private MediaType resolveMediaType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return MediaType.APPLICATION_PDF;
        }
        if (lower.endsWith(".docx")) {
            return MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        }
        // fallback
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
