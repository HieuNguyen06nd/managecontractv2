package com.hieunguyen.ManageContract.service.impl;

import com.hieunguyen.ManageContract.common.constants.VariableType;
import com.hieunguyen.ManageContract.dto.contractTemplate.ContractTemplateResponse;
import com.hieunguyen.ManageContract.entity.AuthAccount;
import com.hieunguyen.ManageContract.entity.ContractTemplate;
import com.hieunguyen.ManageContract.entity.TemplateVariable;
import com.hieunguyen.ManageContract.mapper.ContractTemplateMapper;
import com.hieunguyen.ManageContract.repository.ContractTemplateRepository;
import com.hieunguyen.ManageContract.repository.TemplateVariableRepository;
import com.hieunguyen.ManageContract.service.ContractTemplateService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ContractTemplateServiceImpl implements ContractTemplateService {

    private final ContractTemplateRepository templateRepository;
    private final TemplateVariableRepository variableRepository;

    @Override
    @Transactional
    public ContractTemplateResponse uploadTemplate(MultipartFile file, AuthAccount createdBy) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File không được để trống");
        }

        // Lưu file lên server
        Path targetPath = saveFile(file.getOriginalFilename(), file.getInputStream());

        // Xử lý đọc biến từ file và lưu template
        ContractTemplate template = processTemplateFile(targetPath, createdBy);

        // Trả về DTO
        return ContractTemplateMapper.toResponse(template);
    }

    @Override
    @Transactional
    public ContractTemplateResponse uploadTemplateFromGoogleDoc(String docLink, AuthAccount createdBy) throws IOException {
        if (docLink == null || docLink.isEmpty()) {
            throw new IllegalArgumentException("Link Google Docs không được để trống");
        }

        String fileName = "template_" + System.currentTimeMillis() + ".docx";
        try (InputStream in = new URL(docLink).openStream()) {
            Path targetPath = saveFile(fileName, in);
            ContractTemplate template = processTemplateFile(targetPath, createdBy);
            return ContractTemplateMapper.toResponse(template);
        } catch (IOException e) {
            throw new IOException("Không thể tải file từ link Google Docs", e);
        }
    }

    // --- private helper methods ---

    private Path saveFile(String fileName, InputStream inputStream) throws IOException {
        Path uploadDir = Paths.get("uploads/templates");
        if (!Files.exists(uploadDir)) Files.createDirectories(uploadDir);
        Path targetPath = uploadDir.resolve(fileName);
        Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        return targetPath;
    }

    private ContractTemplate processTemplateFile(Path filePath, AuthAccount createdBy) throws IOException {
        // 1. Đọc nội dung DOCX
        StringBuilder text = new StringBuilder();
        try (XWPFDocument document = new XWPFDocument(Files.newInputStream(filePath))) {
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                text.append(paragraph.getText()).append("\n");
            }
        }

        // 2. Lưu template vào DB
        ContractTemplate template = new ContractTemplate();
        template.setName(filePath.getFileName().toString());
        template.setFilePath(filePath.toString());
        template.setCreatedBy(createdBy);
        template = templateRepository.save(template);

        // 3. Tìm các biến {{varName}} và lưu vào template_variables
        Pattern pattern = Pattern.compile("\\{\\{(.*?)\\}\\}");
        Matcher matcher = pattern.matcher(text.toString());
        Set<String> variableNames = new HashSet<>();
        while (matcher.find()) {
            variableNames.add(matcher.group(1).trim());
        }

        // 4. Lưu biến và cập nhật collection trong template
        List<TemplateVariable> variables = new ArrayList<>();
        for (String varName : variableNames) {
            TemplateVariable variable = new TemplateVariable();
            variable.setVarName(varName);
            variable.setVarType(detectVariableType(varName));
            variable.setRequired(false);
            variable.setTemplate(template);
            variableRepository.save(variable);
            variables.add(variable);
        }
        template.setVariables(variables);

        // 5. Trả về template có variables đầy đủ
        return template;
    }

    /**
     * Nhận diện kiểu dữ liệu từ tên biến
     */
    private VariableType detectVariableType(String varName) {
        String lower = varName.toLowerCase();

        // Kiểu ngày
        if (lower.contains("date") || lower.contains("ngay") || lower.contains("dob")) {
            return VariableType.DATE;
        }

        // Kiểu số
        if (lower.contains("amount") || lower.contains("price") || lower.contains("total") || lower.contains("so")) {
            return VariableType.NUMBER;
        }

        // Kiểu boolean / true false
        if (lower.startsWith("is") || lower.startsWith("has") || lower.contains("active")) {
            return VariableType.BOOLEAN;
        }

        // Kiểu textarea nếu tên biến chứa text hoặc description
        if (lower.contains("description") || lower.contains("note") || lower.contains("content")) {
            return VariableType.TEXTAREA;
        }

        // Dropdown / List có thể detect từ tên biến (ví dụ gender, status, type)
        if (lower.contains("gender") || lower.contains("status") || lower.contains("type")) {
            return VariableType.DROPDOWN;
        }

        // Mặc định
        return VariableType.STRING;
    }

}
