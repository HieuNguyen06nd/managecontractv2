package com.hieunguyen.ManageContract.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hieunguyen.ManageContract.common.exception.ResourceNotFoundException;
import com.hieunguyen.ManageContract.dto.approval.ApprovalFlowResponse;
import com.hieunguyen.ManageContract.dto.contractTemplate.ContractTemplateCreateRequest;
import com.hieunguyen.ManageContract.dto.contractTemplate.ContractTemplateResponse;
import com.hieunguyen.ManageContract.dto.contractTemplate.ContractTemplateUpdateRequest;
import com.hieunguyen.ManageContract.dto.templateVariable.TemplatePreviewResponse;
import com.hieunguyen.ManageContract.dto.templateVariable.TemplateVariablePreview;
import com.hieunguyen.ManageContract.dto.templateVariable.VariableUpdateRequest;
import com.hieunguyen.ManageContract.entity.*;
import com.hieunguyen.ManageContract.mapper.ApprovalFlowMapper;
import com.hieunguyen.ManageContract.mapper.ContractTemplateMapper;
import com.hieunguyen.ManageContract.mapper.TemplateVariableMapper;
import com.hieunguyen.ManageContract.repository.*;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContractTemplateServiceImpl implements ContractTemplateService {

    private final ContractTemplateRepository templateRepository;
    private final TemplateVariableRepository variableRepository;
    private final UserRepository userRepository;
    private final ApprovalFlowRepository approvalFlowRepository;
    private final CategoryRepository categoryRepository;
    private final ObjectMapper objectMapper;


    private static final Path UPLOAD_DIR = Paths.get("uploads", "templates");

    // ------------------ BƯỚC 1: preview từ file (không lưu DB) ------------------
    @Override
    public TemplatePreviewResponse previewFromFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File không được để trống");
        }
        if (!file.getOriginalFilename().toLowerCase().endsWith(".docx")) {
            throw new IllegalArgumentException("Chỉ hỗ trợ file .docx");
        }

        // lưu tạm file với tiền tố tmp_{uuid}
        String tmpFileName = "tmp_" + UUID.randomUUID() + "_" + Objects.requireNonNull(file.getOriginalFilename());
        Path tmpPath = saveFile(tmpFileName, file.getInputStream());

        List<TemplateVariablePreview> variables = parseVariablesFromDocx(tmpPath);

        TemplatePreviewResponse resp = new TemplatePreviewResponse();
        resp.setTempFileName(tmpFileName);
        resp.setVariables(variables);
        return resp;
    }

    // ------------------ BƯỚC 1: preview từ Google Docs (không lưu DB) ------------------
    @Override
    public TemplatePreviewResponse previewFromGoogleDoc(String docLink) throws IOException {
        if (docLink == null || docLink.isEmpty()) {
            throw new IllegalArgumentException("Link Google Docs không được để trống");
        }
        String exportUrl = normalizeGoogleDocsLink(docLink);
        String tmpFileName = "tmp_" + UUID.randomUUID() + "_google_doc.docx";
        try (InputStream in = new URL(exportUrl).openStream()) {
            Path tmpPath = saveFile(tmpFileName, in);
            List<TemplateVariablePreview> variables = parseVariablesFromDocx(tmpPath);

            TemplatePreviewResponse resp = new TemplatePreviewResponse();
            resp.setTempFileName(tmpFileName);
            resp.setVariables(variables);
            return resp;
        } catch (IOException e) {
            throw new IOException("Không thể tải file từ Google Docs. Hãy chắc chắn link share là Public/Anyone with link", e);
        }
    }

    // ------------------ BƯỚC 2: finalize - lưu template + biến vào DB ------------------
    @Override
    @Transactional
    public ContractTemplateResponse finalizeTemplate(ContractTemplateCreateRequest request, Long accountId) {
        if (request == null || request.getTempFileName() == null || request.getTempFileName().isBlank())
            throw new IllegalArgumentException("tempFileName là bắt buộc");

        Employee createdBy = userRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account không tồn tại"));

        Category category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category không tồn tại"));
        }

        try {
            Path tmpPath = UPLOAD_DIR.resolve(request.getTempFileName());
            if (!Files.exists(tmpPath)) throw new ResourceNotFoundException("File tạm không tồn tại trên server");

            String safeNamePart = (request.getName() == null || request.getName().isBlank())
                    ? "template"
                    : request.getName().trim().replaceAll("\\s+", "_");

            String destFileName = safeNamePart + "_" + System.currentTimeMillis() + ".docx";
            Path destPath = UPLOAD_DIR.resolve(destFileName);
            if (!Files.exists(UPLOAD_DIR)) Files.createDirectories(UPLOAD_DIR);
            Files.move(tmpPath, destPath, StandardCopyOption.REPLACE_EXISTING);

            // Tạo template
            ContractTemplate template = ContractTemplate.builder()
                    .name(request.getName() == null ? destFileName : request.getName())
                    .description(request.getDescription())
                    .filePath(destPath.toString())
                    .createdBy(createdBy)
                    .category(category)
                    .build();

            ContractTemplate savedTemplate = templateRepository.save(template);

            // Xử lý các biến thông thường
            final ContractTemplate finalTemplate = savedTemplate;
            List<TemplateVariable> variables = request.getVariables().stream()
                    .map(vreq -> {
                        TemplateVariable variable = TemplateVariableMapper.toEntity(vreq, finalTemplate);

                        // Xử lý config cho các kiểu biến đặc biệt
                        if (vreq.getConfig() != null && !vreq.getConfig().isEmpty()) {
                            try {
                                // Lưu config dưới dạng JSON string
                                variable.setConfig(objectMapper.writeValueAsString(vreq.getConfig()));
                            } catch (Exception e) {
                                // Nếu không parse được JSON, lưu dạng string thông thường
                                variable.setConfig(vreq.getConfig().toString());
                            }
                        }

                        // Xử lý allowedValues cho DROPDOWN/LIST
                        if (vreq.getAllowedValues() != null && !vreq.getAllowedValues().isEmpty()) {
                            variable.setAllowedValues(new ArrayList<>(vreq.getAllowedValues()));
                        }

                        return variable;
                    })
                    .collect(Collectors.toList());

            variableRepository.saveAll(variables);
            savedTemplate.setVariables(variables);

            return ContractTemplateMapper.toResponse(savedTemplate);

        } catch (IOException ex) {
            throw new RuntimeException("Lỗi khi xử lý file template: " + ex.getMessage(), ex);
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

            variable.setVarType(req.getVarType());

            // Cập nhật config nếu có
            if (req.getConfig() != null && !req.getConfig().isEmpty()) {
                try {
                    variable.setConfig(objectMapper.writeValueAsString(req.getConfig()));
                } catch (Exception e) {
                    variable.setConfig(req.getConfig().toString());
                }
            }

            // Cập nhật allowedValues nếu có
            if (req.getAllowedValues() != null && !req.getAllowedValues().isEmpty()) {
                variable.setAllowedValues(new ArrayList<>(req.getAllowedValues()));
            }

            variableRepository.save(variable);
        }
    }
    @Override
    @Transactional
    public ContractTemplateResponse updateTemplate(Long id, ContractTemplateUpdateRequest request) {
        ContractTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Template không tồn tại"));

        if (request.getName() != null && !request.getName().isBlank()) {
            template.setName(request.getName());
        }
        if (request.getDescription() != null) {
            template.setDescription(request.getDescription());
        }

        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category không tồn tại"));
            template.setCategory(category);
        } else {
            template.setCategory(null);
        }

        template = templateRepository.save(template);
        return ContractTemplateMapper.toResponse(template);
    }

    @Override
    public List<ContractTemplateResponse> getAllTemplates() {
        List<ContractTemplate> templates = templateRepository.findAll();

        return templates.stream()
                .map(ContractTemplateMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ApprovalFlowResponse getDefaultFlowByTemplate(Long templateId) {
        var template = templateRepository.findByIdWithDefaultFlow(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("Template không tồn tại"));

        ApprovalFlow defaultFlow = template.getDefaultFlow();
        if (defaultFlow == null) {
            throw new ResourceNotFoundException("Template chưa được đặt luồng mặc định");
        }

        // Nếu muốn trả cả steps chi tiết:
        var flowWithSteps = approvalFlowRepository.findByIdWithSteps(defaultFlow.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy default flow"));

        var resp = ApprovalFlowMapper.toFlowResponse(flowWithSteps);
        resp.setIsDefault(true);
        return resp;
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
        if (docLink.contains("/export?")) return docLink;
        if (docLink.contains("/edit")) return docLink.replaceAll("/edit.*", "/export?format=docx");
        return docLink.endsWith("/") ? docLink + "export?format=docx" : docLink + "/export?format=docx";
    }

    private List<TemplateVariablePreview> parseVariablesFromDocx(Path filePath) throws IOException {
        StringBuilder text = new StringBuilder();
        try (XWPFDocument document = new XWPFDocument(Files.newInputStream(filePath))) {
            document.getParagraphs().forEach(p -> text.append(p.getText()).append("\n"));
            document.getTables().forEach(table ->
                    table.getRows().forEach(row ->
                            row.getTableCells().forEach(cell -> text.append(cell.getText()).append("\n"))
                    )
            );
        }

        List<TemplateVariablePreview> variables = new ArrayList<>();

        // Pattern cho các loại biến
        Pattern simplePattern = Pattern.compile("\\$\\{(.*?)}");
        Pattern booleanPattern = Pattern.compile("\\$\\{(.*?)\\?(.*?):(.*?)}");
        Pattern dropdownPattern = Pattern.compile("\\$\\{(.*?)\\|(.*?)}");
        Pattern tablePattern = Pattern.compile("\\$\\{table:(.*?)}");

        String content = text.toString();

        // Tìm biến boolean: ${var?trueValue:falseValue}
        Matcher booleanMatcher = booleanPattern.matcher(content);
        while (booleanMatcher.find()) {
            String varName = booleanMatcher.group(1).trim();
            String trueLabel = booleanMatcher.group(2).trim();
            String falseLabel = booleanMatcher.group(3).trim();

            // SỬA: Tạo Map thay vì String
            Map<String, Object> configMap = new HashMap<>();
            configMap.put("trueLabel", trueLabel);
            configMap.put("falseLabel", falseLabel);

            TemplateVariablePreview preview = TemplateVariablePreview.builder()
                    .varName(varName)
                    .orderIndex(variables.size() + 1)
                    .varType("BOOLEAN")
                    .config(configMap) // SỬA: Dùng Map thay vì String
                    .build();
            variables.add(preview);
        }

        // Tìm biến dropdown: ${var|option1,option2,option3}
        Matcher dropdownMatcher = dropdownPattern.matcher(content);
        while (dropdownMatcher.find()) {
            String varName = dropdownMatcher.group(1).trim();
            String optionsStr = dropdownMatcher.group(2).trim();
            List<String> options = Arrays.stream(optionsStr.split(","))
                    .map(String::trim)
                    .collect(Collectors.toList());

            // SỬA: Tạo Map thay vì String
            Map<String, Object> configMap = new HashMap<>();
            configMap.put("options", options);

            TemplateVariablePreview preview = TemplateVariablePreview.builder()
                    .varName(varName)
                    .orderIndex(variables.size() + 1)
                    .varType("DROPDOWN")
                    .config(configMap) // SỬA: Dùng Map thay vì String
                    .allowedValues(options)
                    .build();
            variables.add(preview);
        }

        // Tìm biến table: ${table:tableName}
        Matcher tableMatcher = tablePattern.matcher(content);
        while (tableMatcher.find()) {
            String tableName = tableMatcher.group(1).trim();

            // SỬA: Tạo Map thay vì String
            Map<String, Object> configMap = new HashMap<>();
            configMap.put("tableName", tableName);

            TemplateVariablePreview preview = TemplateVariablePreview.builder()
                    .varName("table_" + tableName)
                    .orderIndex(variables.size() + 1)
                    .varType("TABLE")
                    .config(configMap) // SỬA: Dùng Map thay vì String
                    .build();
            variables.add(preview);
        }

        // Tìm biến đơn giản: ${varName}
        Matcher simpleMatcher = simplePattern.matcher(content);
        while (simpleMatcher.find()) {
            String varName = simpleMatcher.group(1).trim();

            // Skip nếu đã được xử lý bởi các pattern trên
            boolean alreadyProcessed = variables.stream()
                    .anyMatch(v -> v.getVarName().equals(varName) ||
                            v.getVarName().equals("table_" + varName));
            if (alreadyProcessed) continue;

            TemplateVariablePreview preview = TemplateVariablePreview.builder()
                    .varName(varName)
                    .orderIndex(variables.size() + 1)
                    .varType("TEXT") // Mặc định
                    .build();
            variables.add(preview);
        }

        return variables;
    }
}
