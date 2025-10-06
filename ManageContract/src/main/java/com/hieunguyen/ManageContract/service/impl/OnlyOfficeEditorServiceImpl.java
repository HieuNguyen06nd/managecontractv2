package com.hieunguyen.ManageContract.service.impl;

import com.hieunguyen.ManageContract.repository.ContractRepository;
import com.hieunguyen.ManageContract.service.OnlyOfficeEditorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OnlyOfficeEditorServiceImpl implements OnlyOfficeEditorService {

    private final ContractRepository contractRepository;

    private String publicDocxUrl(Long id) {
        // DS gọi ngược vào BE
        return "http://host.docker.internal:8080/internal/files/" + id + "/contract.docx";
    }

    private String callbackUrl(Long id) {
        return "http://host.docker.internal:8080/api/onlyoffice/callback/contracts/" + id;
    }

    @Override
    public Map<String, Object> buildEditorConfigForContract(Long contractId) {
        // Đảm bảo file đã tồn tại (nếu cần thì bạn có thể generate trước ở chỗ khác)
        Path docx = Paths.get(System.getProperty("user.dir"),
                "uploads","contracts", String.valueOf(contractId), "contract.docx");
        if (!Files.exists(docx)) {
            throw new RuntimeException("DOCX chưa tồn tại. Hãy generate trước.");
        }

        String fileUrl = publicDocxUrl(contractId);

        Map<String, Object> permissions = new HashMap<>();
        permissions.put("edit", true);
        permissions.put("download", true);
        permissions.put("print", true);
        permissions.put("review", true);

        Map<String, Object> document = new HashMap<>();
        document.put("fileType", "docx");
        document.put("key", contractId + "-" + System.currentTimeMillis()); // key unique theo phiên
        document.put("title", "contract-" + contractId + ".docx");
        document.put("url", fileUrl);
        document.put("permissions", permissions);

        Map<String, Object> editorConfig = new HashMap<>();
        editorConfig.put("mode", "edit");
        editorConfig.put("lang", "vi");
        editorConfig.put("callbackUrl", callbackUrl(contractId));

        Map<String, Object> cfg = new HashMap<>();
        cfg.put("type", "desktop");
        cfg.put("documentType", "word");
        cfg.put("document", document);
        cfg.put("editorConfig", editorConfig);
        return cfg;
    }
}
