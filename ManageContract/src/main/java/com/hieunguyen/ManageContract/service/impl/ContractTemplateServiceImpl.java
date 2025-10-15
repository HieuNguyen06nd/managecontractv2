package com.hieunguyen.ManageContract.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hieunguyen.ManageContract.common.constants.Status;
import com.hieunguyen.ManageContract.common.constants.VariableType;
import com.hieunguyen.ManageContract.common.exception.ResourceNotFoundException;
import com.hieunguyen.ManageContract.dto.approval.ApprovalFlowResponse;
import com.hieunguyen.ManageContract.dto.contractTemplate.ContractTemplateCreateRequest;
import com.hieunguyen.ManageContract.dto.contractTemplate.ContractTemplateResponse;
import com.hieunguyen.ManageContract.dto.contractTemplate.ContractTemplateUpdateRequest;
import com.hieunguyen.ManageContract.dto.templateVariable.TemplatePreviewResponse;
import com.hieunguyen.ManageContract.dto.templateVariable.TemplateVariablePreview;
import com.hieunguyen.ManageContract.dto.templateVariable.TemplateVariableRequest;
import com.hieunguyen.ManageContract.dto.templateVariable.VariableUpdateRequest;
import com.hieunguyen.ManageContract.entity.*;
import com.hieunguyen.ManageContract.mapper.ApprovalFlowMapper;
import com.hieunguyen.ManageContract.mapper.ContractTemplateMapper;
import com.hieunguyen.ManageContract.mapper.TemplateVariableMapper;
import com.hieunguyen.ManageContract.repository.*;
import com.hieunguyen.ManageContract.service.ContractTemplateService;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.scheduling.annotation.Scheduled;
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
@Slf4j
public class ContractTemplateServiceImpl implements ContractTemplateService {

    private final ContractTemplateRepository templateRepository;
    private final TemplateVariableRepository variableRepository;
    private final TemplateTableVariableRepository tableVariableRepository;
    private final TableColumnRepository tableColumnRepository;
    private final UserRepository userRepository;
    private final ApprovalFlowRepository approvalFlowRepository;
    private final CategoryRepository categoryRepository;
    private final ObjectMapper objectMapper;

    private static final Path UPLOAD_DIR = Paths.get("uploads", "templates");

    @PostConstruct
    public void init() {
        try {
            if (!Files.exists(UPLOAD_DIR)) {
                Files.createDirectories(UPLOAD_DIR);
                log.info("Created upload directory: {}", UPLOAD_DIR.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("Could not create upload directory", e);
        }
    }

    // ------------------ BƯỚC 1: preview từ file (không lưu DB) ------------------
    @Override
    public TemplatePreviewResponse previewFromFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File không được để trống");
        }
        if (!file.getOriginalFilename().toLowerCase().endsWith(".docx")) {
            throw new IllegalArgumentException("Chỉ hỗ trợ file .docx");
        }

        // Đảm bảo thư mục tồn tại
        if (!Files.exists(UPLOAD_DIR)) {
            Files.createDirectories(UPLOAD_DIR);
        }

        // Lưu tạm file với tiền tố tmp_{uuid}
        String tmpFileName = "tmp_" + UUID.randomUUID() + "_" + Objects.requireNonNull(file.getOriginalFilename());
        Path tmpPath = saveFile(tmpFileName, file.getInputStream());

        // Verify file was actually saved
        if (!Files.exists(tmpPath)) {
            throw new IOException("Không thể lưu file tạm: " + tmpPath.toAbsolutePath());
        }

        log.info("Temporary file created: {}", tmpPath.toAbsolutePath());

        List<TemplateVariablePreview> variables = parseVariablesFromDocx(tmpPath);

        TemplatePreviewResponse resp = new TemplatePreviewResponse();
        resp.setTempFileName(tmpFileName);
        resp.setVariables(variables);

        log.info("Preview completed with {} variables", variables.size());
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

            log.info("Google Docs preview completed with {} variables", variables.size());
            return resp;
        } catch (IOException e) {
            throw new IOException("Không thể tải file từ Google Docs. Hãy chắc chắn link share là Public/Anyone with link", e);
        }
    }

    // ------------------ BƯỚC 2: finalize - lưu template + biến vào DB ------------------
    @Override
    @Transactional
    public ContractTemplateResponse finalizeTemplate(ContractTemplateCreateRequest request, Long accountId) {
        if (request == null || request.getTempFileName() == null || request.getTempFileName().isBlank()) {
            throw new IllegalArgumentException("tempFileName là bắt buộc");
        }

        Employee createdBy = userRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account không tồn tại"));

        Category category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category không tồn tại"));
        }

        try {
            Path tmpPath = UPLOAD_DIR.resolve(request.getTempFileName());

            // DEBUG: Log detailed file information
            log.info("Looking for temp file: {}", tmpPath.toAbsolutePath());
            log.info("File exists: {}", Files.exists(tmpPath));

            if (!Files.exists(tmpPath)) {
                // List all temp files for debugging
                try {
                    List<String> tempFiles = Files.list(UPLOAD_DIR)
                            .filter(path -> path.getFileName().toString().startsWith("tmp_"))
                            .map(path -> path.getFileName().toString())
                            .collect(Collectors.toList());
                    log.info("Available temp files: {}", tempFiles);
                } catch (IOException e) {
                    log.error("Could not list temp files", e);
                }

                throw new ResourceNotFoundException(
                        "File tạm không tồn tại: " + request.getTempFileName() +
                                ". Có thể đã bị xóa hoặc hết hạn. Vui lòng upload lại file."
                );
            }

            // Verify file is readable
            if (!Files.isReadable(tmpPath)) {
                throw new ResourceNotFoundException("File tạm không thể đọc được: " + tmpPath);
            }

            String safeNamePart = (request.getName() == null || request.getName().isBlank())
                    ? "template"
                    : request.getName().trim().replaceAll("\\s+", "_");

            String destFileName = safeNamePart + "_" + System.currentTimeMillis() + ".docx";
            Path destPath = UPLOAD_DIR.resolve(destFileName);

            log.info("Moving temp file to: {}", destPath.toAbsolutePath());

            Files.move(tmpPath, destPath, StandardCopyOption.REPLACE_EXISTING);

            // Tạo template
            ContractTemplate template = ContractTemplate.builder()
                    .name(request.getName() == null ? destFileName : request.getName())
                    .description(request.getDescription())
                    .filePath(destPath.toString())
                    .status(Status.ACTIVE)
                    .createdBy(createdBy)
                    .category(category)
                    .build();

            ContractTemplate savedTemplate = templateRepository.save(template);

            // DEBUG: Log variables before processing
            log.info("Finalizing template with {} variables", request.getVariables().size());
            request.getVariables().forEach(v -> {
                log.info("Variable: {}, Type: {}, Config: {}", v.getVarName(), v.getVarType(), v.getConfig());
            });

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

            // Xử lý biến TABLE riêng biệt
            List<TemplateTableVariable> tableVariables = processTableVariables(request, savedTemplate);
            savedTemplate.setTableVariables(tableVariables);
            savedTemplate.setVariables(variables);

            // DEBUG: Log tất cả variables đã lưu
            log.info("Saved {} regular variables and {} table variables for template {}",
                    variables.size(), tableVariables.size(), savedTemplate.getId());

            return ContractTemplateMapper.toResponse(savedTemplate);

        } catch (IOException ex) {
            log.error("Error finalizing template", ex);
            throw new RuntimeException("Lỗi khi xử lý file template: " + ex.getMessage(), ex);
        }
    }

    @Override
    @Transactional
    public ContractTemplateResponse toggleTemplateStatus(Long templateId) {
        ContractTemplate t = templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("Template không tồn tại"));

        Status next = (t.getStatus() == Status.ACTIVE) ? Status.INACTIVE : Status.ACTIVE;
        t.setStatus(next);
        templateRepository.save(t);
        return ContractTemplateMapper.toResponse(t);
    }

    @Override
    @Transactional
    public ContractTemplateResponse updateTemplateStatus(Long templateId, Status status) {
        var t = templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("Template không tồn tại"));
        t.setStatus(status);
        templateRepository.save(t);
        return ContractTemplateMapper.toResponse(t);
    }

    @Override
    public List<ContractTemplateResponse> getAllTemplatesByStatus(Status status) {
        List<ContractTemplate> templates = (status == null)
                ? templateRepository.findAll()
                : templateRepository.findAllByStatus(status);

        log.info("Retrieved {} templates (status={})", templates.size(), status);
        return templates.stream()
                .map(ContractTemplateMapper::toResponse)
                .collect(Collectors.toList());
    }

    // Xử lý biến TABLE riêng biệt
    private List<TemplateTableVariable> processTableVariables(ContractTemplateCreateRequest request, ContractTemplate template) {
        List<TemplateTableVariable> tableVariables = new ArrayList<>();

        // Lọc các biến có type là TABLE
        List<TemplateVariableRequest> tableVariableRequests = request.getVariables().stream()
                .filter(v ->VariableType.TABLE.equals(v.getVarType()))
                .collect(Collectors.toList());

        for (TemplateVariableRequest tableReq : tableVariableRequests) {
            TemplateTableVariable tableVariable = new TemplateTableVariable();
            tableVariable.setTableName(extractTableName(tableReq.getVarName()));
            tableVariable.setDisplayName(tableReq.getName());
            tableVariable.setTemplate(template);
            tableVariable.setMinRows(1);
            tableVariable.setMaxRows(10);
            tableVariable.setOrderIndex(tableReq.getOrderIndex());

            // Xử lý columns từ config
            List<TableColumn> columns = extractColumnsFromConfig(tableReq.getConfig(), tableVariable);
            tableVariable.setColumns(columns);

            tableVariables.add(tableVariable);
        }

        tableVariableRepository.saveAll(tableVariables);
        return tableVariables;
    }

    private String extractTableName(String varName) {
        // Biến TABLE có format "table_{tableName}" -> extract tableName
        if (varName.startsWith("table_")) {
            return varName.substring(6); // Remove "table_" prefix
        }
        return varName;
    }


    private List<TableColumn> extractColumnsFromConfig(Map<String, Object> config, TemplateTableVariable tableVariable) {
        List<TableColumn> columns = new ArrayList<>();

        if (config != null && config.containsKey("columns")) {
            try {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> columnConfigs = (List<Map<String, Object>>) config.get("columns");

                log.info("Extracting {} columns from config", columnConfigs.size());

                int columnOrder = 0;
                for (Map<String, Object> columnConfig : columnConfigs) {
                    TableColumn column = new TableColumn();

                    // Xử lý column name
                    String columnName = (String) columnConfig.get("name");
                    column.setColumnName(columnName != null ? columnName : "column_" + (columnOrder + 1));
                    column.setDisplayName(generateDisplayName(column.getColumnName()));

                    // FIX: Xử lý cả enum và string cho column type
                    Object typeObj = columnConfig.get("type");
                    VariableType columnType;

                    if (typeObj instanceof VariableType) {
                        columnType = (VariableType) typeObj;
                    } else if (typeObj instanceof String) {
                        columnType = convertStringToVariableType((String) typeObj);
                    } else {
                        log.warn("Unknown column type: {}, defaulting to STRING", typeObj);
                        columnType = VariableType.STRING;
                    }

                    column.setColumnType(columnType);
                    column.setRequired(true);
                    column.setColumnOrder(columnOrder++);
                    column.setTableVariable(tableVariable);

                    columns.add(column);
                    log.debug("Created column: {}, type: {}", column.getColumnName(), column.getColumnType());
                }
            } catch (Exception e) {
                log.error("Error parsing column config: {}", config, e);
                // Fallback: tạo columns mặc định
                return createDefaultColumns(tableVariable);
            }
        } else {
            log.warn("No columns config found, using default columns");
            // Tạo columns mặc định nếu không có config
            return createDefaultColumns(tableVariable);
        }

        return columns;
    }
    // Method chuyển đổi String sang VariableType enum - FIX LỖI
    private VariableType convertStringToVariableType(String typeStr) {
        if (typeStr == null) {
            return VariableType.STRING;
        }

        try {
            return VariableType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown variable type: {}, defaulting to STRING", typeStr);
            return VariableType.STRING;
        }
    }

    private List<TableColumn> createDefaultColumns(TemplateTableVariable tableVariable) {
        List<TableColumn> columns = new ArrayList<>();

        TableColumn col1 = new TableColumn();
        col1.setColumnName("column_1");
        col1.setDisplayName("Cột 1");
        col1.setColumnType(VariableType.STRING);
        col1.setRequired(true);
        col1.setColumnOrder(0);
        col1.setTableVariable(tableVariable);
        columns.add(col1);

        TableColumn col2 = new TableColumn();
        col2.setColumnName("column_2");
        col2.setDisplayName("Cột 2");
        col2.setColumnType(VariableType.STRING);
        col2.setRequired(true);
        col2.setColumnOrder(1);
        col2.setTableVariable(tableVariable);
        columns.add(col2);

        return columns;
    }

    private String generateDisplayName(String columnName) {
        // Chuyển "column_name" thành "Column Name"
        return Arrays.stream(columnName.split("_"))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    @Transactional
    @Override
    public void updateVariableTypes(Long templateId, List<VariableUpdateRequest> requests) {
        ContractTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("Template không tồn tại"));

        for (VariableUpdateRequest req : requests) {
            TemplateVariable variable = variableRepository.findByTemplateAndVarName(template, req.getVarName())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy biến: " + req.getVarName()));

            // FIX: Chuyển đổi String sang VariableType enum
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
            log.info("Updated variable: {}, Type: {}", variable.getVarName(), variable.getVarType());
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
        log.info("Updated template: {}", template.getId());
        return ContractTemplateMapper.toResponse(template);
    }

    @Override
    public List<ContractTemplateResponse> getAllTemplates() {
        List<ContractTemplate> templates = templateRepository.findAll();
        log.info("Retrieved {} templates", templates.size());
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

        var flowWithSteps = approvalFlowRepository.findByIdWithSteps(defaultFlow.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy default flow"));

        var resp = ApprovalFlowMapper.toFlowResponse(flowWithSteps);
        resp.setIsDefault(true);
        return resp;
    }

    // ------------------ HELPER METHODS ------------------

    private Path saveFile(String fileName, InputStream inputStream) throws IOException {
        // Use the same UPLOAD_DIR constant
        if (!Files.exists(UPLOAD_DIR)) {
            Files.createDirectories(UPLOAD_DIR);
        }
        Path targetPath = UPLOAD_DIR.resolve(fileName);
        Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        log.info("File saved: {}", targetPath.toAbsolutePath());
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
        String content = text.toString();

        log.info("Parsing document content, length: {}", content.length());

        // Pattern cho các loại biến
        Pattern simplePattern = Pattern.compile("\\$\\{(.*?)}");
        Pattern booleanPattern = Pattern.compile("\\$\\{(.*?)\\?(.*?):(.*?)}");
        Pattern dropdownPattern = Pattern.compile("\\$\\{(.*?)\\|(.*?)}");
        Pattern tablePattern = Pattern.compile("\\$\\{table:(.*?)}");

        // Tìm biến TABLE trước (quan trọng)
        Matcher tableMatcher = tablePattern.matcher(content);
        while (tableMatcher.find()) {
            String tableName = tableMatcher.group(1).trim();

            Map<String, Object> configMap = new HashMap<>();
            configMap.put("tableName", tableName);

            // Cấu hình mặc định cho bảng - SỬA: Sử dụng enum trong columns
            List<Map<String, Object>> columns = new ArrayList<>();
            columns.add(createColumnWithEnum("column_1", VariableType.TEXT)); // SỬA
            columns.add(createColumnWithEnum("column_2", VariableType.TEXT)); // SỬA
            configMap.put("columns", columns);

            // Các thuộc tính bổ sung cho table
            configMap.put("minRows", 1);
            configMap.put("maxRows", 10);
            configMap.put("editable", true);

            TemplateVariablePreview preview = TemplateVariablePreview.builder()
                    .varName("table_" + tableName)
                    .orderIndex(variables.size() + 1)
                    .varType(VariableType.TABLE)
                    .config(configMap)
                    .allowedValues(new ArrayList<>())
                    .build();
            variables.add(preview);
            log.info("Found TABLE variable: {} with config: {}", "table_" + tableName, configMap);
        }

        // Tìm biến boolean: ${var?trueValue:falseValue}
        Matcher booleanMatcher = booleanPattern.matcher(content);
        while (booleanMatcher.find()) {
            String varName = booleanMatcher.group(1).trim();

            // Skip nếu đã được xử lý như table
            if (isAlreadyProcessed(varName, variables)) continue;

            String trueLabel = booleanMatcher.group(2).trim();
            String falseLabel = booleanMatcher.group(3).trim();

            Map<String, Object> configMap = new HashMap<>();
            configMap.put("trueLabel", trueLabel);
            configMap.put("falseLabel", falseLabel);

            TemplateVariablePreview preview = TemplateVariablePreview.builder()
                    .varName(varName)
                    .orderIndex(variables.size() + 1)
                    .varType(VariableType.BOOLEAN)
                    .config(configMap)
                    .build();
            variables.add(preview);
            log.debug("Found BOOLEAN variable: {}", varName);
        }

        // Tìm biến dropdown: ${var|option1,option2,option3}
        Matcher dropdownMatcher = dropdownPattern.matcher(content);
        while (dropdownMatcher.find()) {
            String varName = dropdownMatcher.group(1).trim();

            // Skip nếu đã được xử lý như table
            if (isAlreadyProcessed(varName, variables)) continue;

            String optionsStr = dropdownMatcher.group(2).trim();
            List<String> options = Arrays.stream(optionsStr.split(","))
                    .map(String::trim)
                    .collect(Collectors.toList());

            Map<String, Object> configMap = new HashMap<>();
            configMap.put("options", options);

            TemplateVariablePreview preview = TemplateVariablePreview.builder()
                    .varName(varName)
                    .orderIndex(variables.size() + 1)
                    .varType(VariableType.DROPDOWN)
                    .config(configMap)
                    .allowedValues(options)
                    .build();
            variables.add(preview);
            log.debug("Found DROPDOWN variable: {} with options: {}", varName, options);
        }

        // Tìm biến đơn giản: ${varName}
        Matcher simpleMatcher = simplePattern.matcher(content);
        while (simpleMatcher.find()) {
            String varName = simpleMatcher.group(1).trim();

            // Skip nếu đã được xử lý bởi các pattern trên
            if (isAlreadyProcessed(varName, variables)) continue;

            TemplateVariablePreview preview = TemplateVariablePreview.builder()
                    .varName(varName)
                    .orderIndex(variables.size() + 1)
                    .varType(VariableType.TEXT) // Mặc định
                    .config(new HashMap<>())
                    .build();
            variables.add(preview);
            log.debug("Found TEXT variable: {}", varName);
        }

        log.info("Total variables parsed: {}", variables.size());
        return variables;
    }

    // THÊM: Helper method mới để tạo column với enum
    private Map<String, Object> createColumnWithEnum(String name, VariableType type) {
        Map<String, Object> column = new HashMap<>();
        column.put("name", name);
        column.put("type", type.name()); // Lưu dạng string để FE hiểu
        return column;
    }
    // Helper method để tạo column cho TABLE
    private Map<String, String> createColumn(String name, String type) {
        Map<String, String> column = new HashMap<>();
        column.put("name", name);
        column.put("type", type);
        return column;
    }

    // Helper method kiểm tra biến đã xử lý
    private boolean isAlreadyProcessed(String varName, List<TemplateVariablePreview> variables) {
        return variables.stream()
                .anyMatch(v -> v.getVarName().equals(varName) ||
                        v.getVarName().equals("table_" + varName));
    }

    // Scheduled cleanup for temp files (chạy mỗi giờ)
    @Scheduled(fixedRate = 3600000)
    public void cleanupTempFiles() {
        try {
            if (!Files.exists(UPLOAD_DIR)) return;

            List<Path> tempFiles = Files.list(UPLOAD_DIR)
                    .filter(path -> path.getFileName().toString().startsWith("tmp_"))
                    .filter(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toMillis() <
                                    System.currentTimeMillis() - 3600000; // 1 hour
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .collect(Collectors.toList());

            for (Path tempFile : tempFiles) {
                try {
                    Files.deleteIfExists(tempFile);
                    log.info("Cleaned up temp file: {}", tempFile.getFileName());
                } catch (IOException e) {
                    log.error("Could not delete temp file: {}", tempFile, e);
                }
            }
        } catch (IOException e) {
            log.error("Error during temp file cleanup", e);
        }
    }
}