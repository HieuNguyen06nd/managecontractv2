package com.hieunguyen.ManageContract.service.impl;

import com.hieunguyen.ManageContract.common.exception.ResourceNotFoundException;
import com.hieunguyen.ManageContract.dto.contractTemplate.ContractTemplateResponse;
import com.hieunguyen.ManageContract.dto.templateVariable.VariableUpdateRequest;
import com.hieunguyen.ManageContract.entity.ContractTemplate;
import com.hieunguyen.ManageContract.entity.TemplateVariable;
import com.hieunguyen.ManageContract.entity.User;
import com.hieunguyen.ManageContract.mapper.ContractTemplateMapper;
import com.hieunguyen.ManageContract.repository.ContractTemplateRepository;
import com.hieunguyen.ManageContract.repository.TemplateVariableRepository;
import com.hieunguyen.ManageContract.repository.UserRepository;
import com.hieunguyen.ManageContract.service.ContractTemplateService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ContractTemplateServiceImpl implements ContractTemplateService {

    private final ContractTemplateRepository templateRepository;
    private final TemplateVariableRepository variableRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public ContractTemplateResponse uploadTemplate(MultipartFile file,  Long accountId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File không được để trống");
        }

        User createdBy = userRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account không tồn tại"));

        Path targetPath = saveFile(file.getOriginalFilename(), file.getInputStream());
        ContractTemplate template = processDocxFile(targetPath, createdBy);

        return ContractTemplateMapper.toResponse(template);
    }

    @Override
    @Transactional
    public ContractTemplateResponse uploadTemplateFromGoogleDoc(String docLink, Long accountId) throws IOException {
        if (docLink == null || docLink.isEmpty()) {
            throw new IllegalArgumentException("Link Google Docs không được để trống");
        }

        User createdBy = userRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account không tồn tại"));

        // link share → export docx
        String exportUrl = normalizeGoogleDocsLink(docLink);

        String fileName = "template_" + System.currentTimeMillis() + ".docx";
        try (InputStream in = new URL(exportUrl).openStream()) {
            Path targetPath = saveFile(fileName, in);
            ContractTemplate template = processDocxFile(targetPath, createdBy);
            return ContractTemplateMapper.toResponse(template);
        } catch (IOException e) {
            throw new IOException("Không thể tải file từ Google Docs. Hãy chắc chắn link share là Public/Anyone with link", e);
        }
    }

    @Transactional
    @Override
    public void updateVariableTypes(Long templateId, List<VariableUpdateRequest> requests) {
        ContractTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("Template không tồn tại"));

        for (VariableUpdateRequest req : requests) {
            TemplateVariable variable = variableRepository.findByTemplateAndVarName(template, req.getVarName())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy biến: " + req.getVarName()));

            variable.setVarType(req.getVarType()); // user chọn từ FE
            variableRepository.save(variable);
        }
    }

    // --- helper methods ---
    private Path saveFile(String fileName, InputStream inputStream) throws IOException {
        Path uploadDir = Paths.get("uploads/templates");
        if (!Files.exists(uploadDir)) Files.createDirectories(uploadDir);
        Path targetPath = uploadDir.resolve(fileName);
        Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        return targetPath;
    }


    private String normalizeGoogleDocsLink(String docLink) {
        if (docLink.contains("/export?")) {
            return docLink;
        }
        if (docLink.contains("/edit")) {
            return docLink.replaceAll("/edit.*", "/export?format=docx");
        }
        return docLink.endsWith("/") ? docLink + "export?format=docx" : docLink + "/export?format=docx";
    }

    /**
     * Xử lý file DOCX: đọc nội dung, tìm biến ${var}, lưu vào DB
     */
    private ContractTemplate processDocxFile(Path filePath, User createdBy) throws IOException {
        StringBuilder text = new StringBuilder();

        try (XWPFDocument document = new XWPFDocument(Files.newInputStream(filePath))) {
            // đọc paragraph
            document.getParagraphs().forEach(p -> text.append(p.getText()).append("\n"));

            // đọc bảng
            document.getTables().forEach(table ->
                    table.getRows().forEach(row ->
                            row.getTableCells().forEach(cell ->
                                    text.append(cell.getText()).append("\n")
                            )
                    )
            );
        }

        return saveTemplateAndVariables(filePath, createdBy, text.toString());
    }

    /**
     * Lưu template và biến
     */
    private ContractTemplate saveTemplateAndVariables(Path filePath, User createdBy, String text) {
        ContractTemplate template = new ContractTemplate();
        template.setName(filePath.getFileName().toString());
        template.setFilePath(filePath.toString());
        template.setCreatedBy(createdBy);
        template = templateRepository.save(template);

        // Regex ${varName}
        Pattern pattern = Pattern.compile("\\$\\{(.*?)}");
        Matcher matcher = pattern.matcher(text);

        List<TemplateVariable> variables = new ArrayList<>();
        int order = 1;
        while (matcher.find()) {
            String varName = matcher.group(1).trim();

            // tránh trùng tên -> nếu đã có thì bỏ qua
            boolean exists = variables.stream()
                    .anyMatch(v -> v.getVarName().equalsIgnoreCase(varName));
            if (exists) continue;

            TemplateVariable variable = new TemplateVariable();
            variable.setVarName(varName);
            variable.setVarType(null);       // chưa chọn, FE sẽ cập nhật sau
            variable.setRequired(false);     // mặc định chưa bắt buộc
            variable.setOrderIndex(order++); // gán STT theo thứ tự đọc trong file
            variable.setTemplate(template);

            variableRepository.save(variable);
            variables.add(variable);
        }
        template.setVariables(variables);

        return template;
    }

}
