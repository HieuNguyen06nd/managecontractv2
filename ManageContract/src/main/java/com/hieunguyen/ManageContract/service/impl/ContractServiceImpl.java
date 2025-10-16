package com.hieunguyen.ManageContract.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hieunguyen.ManageContract.common.constants.ApprovalStatus;
import com.hieunguyen.ManageContract.common.constants.ApproverType;
import com.hieunguyen.ManageContract.common.constants.ContractStatus;
import com.hieunguyen.ManageContract.common.constants.DocxToHtmlConverter;
import com.hieunguyen.ManageContract.dto.contract.ContractResponse;
import com.hieunguyen.ManageContract.dto.contract.CreateContractRequest;
import com.hieunguyen.ManageContract.entity.*;
import com.hieunguyen.ManageContract.mapper.ContractMapper;
import com.hieunguyen.ManageContract.repository.*;
import com.hieunguyen.ManageContract.service.ContractService;
import jakarta.transaction.Transactional;
import jakarta.xml.bind.JAXBElement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.*;
import org.docx4j.model.datastorage.migration.VariablePrepare;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.ContentAccessor;
import org.docx4j.wml.Text;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContractServiceImpl implements ContractService {
    private final ContractRepository contractRepository;
    private final ContractVariableValueRepository variableValueRepository;
    private final ContractTemplateRepository templateRepository;
    private final ContractApprovalRepository contractApprovalRepository;
    private final UserRepository userRepository;
    private final PositionRepository positionRepository;
    private final DepartmentRepository departmentRepository;
    private final ApprovalFlowRepository approvalFlowRepository;
    private final OnlyOfficeConvertService onlyOfficeConvertService;

    @Value("${app.ds.url:http://localhost:8081}")
    private String docServer; // URL Document Server (OnlyOffice)

    @Value("${app.ds.source-base:http://host.docker.internal:8080}")
    private String hostBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    // ======================== CREATE ========================
    @Transactional
    @Override
    public ContractResponse createContract(CreateContractRequest request) {
        // Template
        ContractTemplate template = templateRepository.findById(request.getTemplateId())
                .orElseThrow(() -> new RuntimeException("Template not found"));

        // Creator
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Employee createdBy = userRepository.findByAccount_Email(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nhân viên với email: " + email));

        // New contract (DRAFT)
        Contract contract = new Contract();
        contract.setTitle(request.getTitle());
        contract.setContractNumber("HD-" + System.currentTimeMillis());
        contract.setTemplate(template);
        contract.setCreatedBy(createdBy);
        contract.setStatus(ContractStatus.DRAFT);

        Contract savedContract = contractRepository.save(contract);

        final Contract contractRef = savedContract;
        // Save variables
        List<ContractVariableValue> values = Optional.ofNullable(request.getVariables())
                .orElse(List.of())
                .stream()
                .map(v -> {
                    ContractVariableValue cv = new ContractVariableValue();
                    cv.setContract(contractRef);
                    cv.setVarName(v.getVarName());
                    cv.setVarValue(v.getVarValue());
                    return cv;
                })
                .collect(Collectors.toList());
        variableValueRepository.saveAll(values);
        savedContract.setVariableValues(values);

        // -------- FIX: ƯU TIÊN flowId nếu FE gửi vào --------
        ApprovalFlow flow = null;
        if (request.getFlowId() != null) {
            flow = approvalFlowRepository.findById(request.getFlowId())
                    .orElseThrow(() -> new RuntimeException("Approval flow not found"));
            savedContract.setFlow(flow);
        } else {
            String opt = Optional.ofNullable(request.getFlowOption()).orElse("").toLowerCase(Locale.ROOT);
            switch (opt) {
                case "default" -> {
                    if (template.getDefaultFlow() != null) {
                        flow = template.getDefaultFlow();
                        savedContract.setFlow(flow);
                    }
                }
                case "existing" -> {
                    if (request.getExistingFlowId() != null) {
                        flow = approvalFlowRepository.findById(request.getExistingFlowId())
                                .orElseThrow(() -> new RuntimeException("Approval flow not found"));
                        savedContract.setFlow(flow);
                    }
                }
                case "new" -> {
                    // Tạo flow mới đúng chuẩn, step trỏ về flow này
                    flow = createApprovalFlow(request, template);
                    savedContract.setFlow(flow);
                }
                default -> {
                    // Không chọn gì -> để null (Draft vẫn OK), nhưng nếu bạn muốn ép phải có flow thì throw ở đây.
                }
            }
        }
        // -----------------------------------------------------

        savedContract = contractRepository.save(savedContract);

        // Generate file (best-effort)
        try {
            String filePath = generateContractFileFromTemplate(savedContract);
            savedContract.setFilePath(filePath);
            savedContract = contractRepository.save(savedContract);
            log.info("Created contract file for contract {}: {}", savedContract.getId(), filePath);
        } catch (Exception e) {
            log.warn("Could not create contract file for contract {}: {}", savedContract.getId(), e.getMessage());
        }

        return ContractMapper.toResponse(savedContract);
    }

    // ======================== PREVIEW (PDF) ========================
    @Override
    public byte[] previewContractPdf(CreateContractRequest request) {
        ContractTemplate template = templateRepository.findById(request.getTemplateId())
                .orElseThrow(() -> new RuntimeException("Template not found"));

        Path templatePath = Paths.get(template.getFilePath());
        if (!Files.exists(templatePath)) throw new RuntimeException("Template file not found: " + templatePath);
        if (!templatePath.getFileName().toString().toLowerCase().endsWith(".docx")) {
            throw new RuntimeException("Only DOCX template is supported for preview");
        }

        String token = UUID.randomUUID().toString();
        Path previewDir = Paths.get("uploads", "previews", token);
        Path docxOut = previewDir.resolve("contract.docx");
        try {
            Files.createDirectories(previewDir);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create preview dir: " + e.getMessage(), e);
        }

        Map<String, String> vars = Optional.ofNullable(request.getVariables()).orElse(List.of())
                .stream()
                .collect(Collectors.toMap(
                        CreateContractRequest.VariableValueRequest::getVarName,
                        v -> Optional.ofNullable(v.getVarValue()).orElse("")
                ));

        try {
            WordprocessingMLPackage pkg = WordprocessingMLPackage.load(templatePath.toFile());
            VariablePrepare.prepare(pkg);
            pkg.getMainDocumentPart().variableReplace(vars);
            pkg.save(docxOut.toFile());
        } catch (Exception e) {
            throw new RuntimeException("Build preview DOCX failed: " + e.getMessage(), e);
        }

        String sourceUrl = hostBaseUrl.replaceAll("/+$", "") + "/internal/previews/" + token + "/contract.docx";
        byte[] pdfBytes = convertDocxUrlToPdf(sourceUrl);

        try {
            Files.write(previewDir.resolve("contract.pdf"), pdfBytes,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ignore) {
        }

        return pdfBytes;
    }

    private byte[] convertDocxUrlToPdf(String sourceUrl) {
        try {
            String base = docServer.replaceAll("/+$", "");
            String[] endpoints = {base + "/ConvertService.ashx", base + "/ConvertService"};

            Map<String, Object> payload = new HashMap<>();
            payload.put("async", false);
            payload.put("filetype", "docx");
            payload.put("outputtype", "pdf");
            payload.put("key", "preview-" + System.currentTimeMillis());
            payload.put("title", "contract.docx");
            payload.put("url", sourceUrl);

            String json = mapper.writeValueAsString(payload);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            ResponseEntity<String> resp = null;
            for (String ep : endpoints) {
                try {
                    resp = restTemplate.postForEntity(ep, new HttpEntity<>(json, headers), String.class);
                    if (resp.getStatusCode().is2xxSuccessful()) break;
                } catch (Exception ignore) {
                }
            }
            if (resp == null || !resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                throw new RuntimeException("ConvertService request failed");
            }

            var root = mapper.readTree(resp.getBody());
            int attempts = 0;
            while (!root.path("endConvert").asBoolean(false) && attempts < 20) {
                Thread.sleep(400);
                resp = restTemplate.postForEntity(endpoints[0], new HttpEntity<>(json, headers), String.class);
                if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                    throw new RuntimeException("ConvertService polling failed");
                }
                root = mapper.readTree(resp.getBody());
                attempts++;
            }
            if (!root.path("endConvert").asBoolean(false)) {
                throw new RuntimeException("Convert not finished (timeout)");
            }

            String fileUrl = root.path("fileUrl").asText(null);
            if (fileUrl == null) throw new RuntimeException("ConvertService returned no fileUrl");
            byte[] pdf = restTemplate.getForObject(fileUrl, byte[].class);
            if (pdf == null || pdf.length == 0) throw new RuntimeException("Downloaded PDF is empty");
            return pdf;
        } catch (Exception e) {
            throw new RuntimeException("Preview convert failed: " + e.getMessage(), e);
        }
    }

    // ======================== FILE GENERATION ========================
    private String generateContractFileFromTemplate(Contract contract) {
        try {
            ContractTemplate template = contract.getTemplate();
            if (template == null || template.getFilePath() == null) {
                throw new RuntimeException("Template not found or template file path is null");
            }

            Path templatePath = Path.of(template.getFilePath());
            if (!Files.exists(templatePath)) {
                throw new RuntimeException("Template file does not exist: " + template.getFilePath());
            }

            Path contractDir = Paths.get("uploads", "contracts", String.valueOf(contract.getId()));
            Files.createDirectories(contractDir);

            Path pdfPath = contractDir.resolve("contract_" + System.currentTimeMillis() + ".pdf");
            String fileName = templatePath.getFileName().toString().toLowerCase();

            if (fileName.endsWith(".docx")) {
                processDocxToPdf(templatePath, pdfPath, contract);
            } else {
                processTextToPdf(templatePath, pdfPath, contract);
            }

            log.info("Created contract file from template at: {}", pdfPath);
            return pdfPath.toString();

        } catch (Exception e) {
            log.error("Error generating contract file from template: {}", e.getMessage());
            throw new RuntimeException("Failed to generate contract file from template: " + e.getMessage(), e);
        }
    }

    private void processDocxToPdf(Path templatePath, Path pdfPath, Contract contract) {
        try {
            Path tempDocxPath = pdfPath.getParent().resolve("temp_contract_" + System.currentTimeMillis() + ".docx");

            replaceVariablesInDocxFile(templatePath, tempDocxPath, contract.getVariableValues());

            boolean success = onlyOfficeConvertService.convertDocxToPdf(tempDocxPath, pdfPath);

            if (!success) {
                log.warn("OnlyOffice conversion failed, falling back to improved PDFBox");
                convertWithPdfBoxImproved(tempDocxPath, pdfPath, contract);
            } else {
                addSignaturePlaceholdersToExistingPdf(pdfPath, contract);
            }

            Files.deleteIfExists(tempDocxPath);

            log.info("✅ SUCCESS: Processed DOCX to PDF for contract {}", contract.getId());

        } catch (Exception e) {
            throw new RuntimeException("Failed to process DOCX template: " + e.getMessage(), e);
        }
    }

    private void addSignaturePlaceholdersToExistingPdf(Path pdfPath, Contract contract) {
        if (contract.getFlow() == null || contract.getFlow().getSteps() == null) {
            return;
        }

        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            PDPage lastPage = document.getPage(document.getNumberOfPages() - 1);
            PDFont font = loadUnicodeFont(document);

            try (PDPageContentStream contentStream = new PDPageContentStream(
                    document, lastPage, PDPageContentStream.AppendMode.APPEND, true, true)) {

                float yPosition = 150;
                float margin = 50;

                contentStream.setFont(font, 10);

                contentStream.beginText();
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText("CHỮ KÝ XÁC NHẬN:");
                contentStream.endText();
                yPosition -= 20;

                for (ApprovalStep step : contract.getFlow().getSteps()) {
                    if (step.getSignaturePlaceholder() != null && !step.getSignaturePlaceholder().isBlank()) {
                        if (yPosition < 50) break;
                        contentStream.beginText();
                        contentStream.newLineAtOffset(margin, yPosition);
                        contentStream.showText(step.getSignaturePlaceholder() + ": _________________________");
                        contentStream.endText();
                        yPosition -= 25;
                    }
                }
            }

            Path tempPdf = pdfPath.getParent().resolve("temp_with_signatures_" + System.currentTimeMillis() + ".pdf");
            document.save(tempPdf.toFile());
            Files.move(tempPdf, pdfPath, StandardCopyOption.REPLACE_EXISTING);

            log.info("✅ Added signature placeholders to PDF: {}", pdfPath);

        } catch (Exception e) {
            log.warn("⚠️ Could not add signature placeholders to PDF: {}", e.getMessage());
        }
    }

    private void replaceVariablesInDocxFile(Path sourceDocx, Path targetDocx, List<ContractVariableValue> variables) {
        try {
            Files.copy(sourceDocx, targetDocx, StandardCopyOption.REPLACE_EXISTING);

            Map<String, String> variableMap = new HashMap<>();
            for (ContractVariableValue var : variables) {
                variableMap.put(var.getVarName(), var.getVarValue() != null ? var.getVarValue() : "");
            }

            try (XWPFDocument document = new XWPFDocument(Files.newInputStream(targetDocx))) {
                // paragraphs
                for (XWPFParagraph paragraph : document.getParagraphs()) {
                    replaceVariablesInParagraph(paragraph, variableMap);
                }
                // tables
                for (XWPFTable table : document.getTables()) {
                    for (XWPFTableRow row : table.getRows()) {
                        for (XWPFTableCell cell : row.getTableCells()) {
                            for (XWPFParagraph paragraph : cell.getParagraphs()) {
                                replaceVariablesInParagraph(paragraph, variableMap);
                            }
                        }
                    }
                }
                // headers
                for (XWPFHeader header : document.getHeaderList()) {
                    for (XWPFParagraph paragraph : header.getParagraphs()) {
                        replaceVariablesInParagraph(paragraph, variableMap);
                    }
                }
                // footers
                for (XWPFFooter footer : document.getFooterList()) {
                    for (XWPFParagraph paragraph : footer.getParagraphs()) {
                        replaceVariablesInParagraph(paragraph, variableMap);
                    }
                }

                try (FileOutputStream out = new FileOutputStream(targetDocx.toFile())) {
                    document.write(out);
                }
            }

            log.info("✅ Successfully replaced variables in DOCX: {}", targetDocx);

        } catch (Exception e) {
            throw new RuntimeException("Failed to replace variables in DOCX: " + e.getMessage(), e);
        }
    }

    private void replaceVariablesInParagraph(XWPFParagraph paragraph, Map<String, String> variableMap) {
        String paragraphText = paragraph.getText();
        if (paragraphText == null || paragraphText.isEmpty()) return;

        String newText = paragraphText;
        for (Map.Entry<String, String> entry : variableMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            newText = newText.replace("${" + key + "}", value);
            newText = newText.replace("{{" + key + "}}", value);
        }

        if (!newText.equals(paragraphText)) {
            for (int i = paragraph.getRuns().size() - 1; i >= 0; i--) {
                paragraph.removeRun(i);
            }
            XWPFRun newRun = paragraph.createRun();
            newRun.setText(newText);
            if (!paragraph.getRuns().isEmpty()) {
                XWPFRun firstRun = paragraph.getRuns().get(0);
                newRun.setBold(firstRun.isBold());
                newRun.setItalic(firstRun.isItalic());
                newRun.setFontSize(firstRun.getFontSize());
                newRun.setFontFamily(firstRun.getFontFamily());
                newRun.setColor(firstRun.getColor());
            }
        }
    }

    private void convertWithPdfBoxImproved(Path docxPath, Path pdfPath, Contract contract) {
        try (XWPFDocument docxDocument = new XWPFDocument(Files.newInputStream(docxPath));
             PDDocument pdfDocument = new PDDocument()) {

            PDFont font = loadUnicodeFont(pdfDocument);
            float currentY = 750;
            float margin = 50;
            float lineHeight = 12;

            PDPage currentPage = new PDPage(PDRectangle.A4);
            pdfDocument.addPage(currentPage);
            PDPageContentStream contentStream = new PDPageContentStream(pdfDocument, currentPage);

            contentStream.setFont(font, 10);
            contentStream.beginText();
            contentStream.newLineAtOffset(margin, currentY);

            for (XWPFParagraph paragraph : docxDocument.getParagraphs()) {
                String text = paragraph.getText();
                if (text != null && !text.trim().isEmpty()) {

                    if (currentY < 100) {
                        contentStream.endText();
                        contentStream.close();

                        currentPage = new PDPage(PDRectangle.A4);
                        pdfDocument.addPage(currentPage);
                        contentStream = new PDPageContentStream(pdfDocument, currentPage);
                        currentY = 750;

                        contentStream.setFont(font, 10);
                        contentStream.beginText();
                        contentStream.newLineAtOffset(margin, currentY);
                    }

                    List<String> lines = wrapText(text, font, 10, 500);
                    for (String line : lines) {
                        contentStream.showText(line);
                        contentStream.newLineAtOffset(0, -lineHeight);
                        currentY -= lineHeight;
                    }
                }
            }

            contentStream.endText();
            contentStream.close();

            addSignaturePlaceholdersFromFlow(pdfDocument, currentPage, font, contract);

            pdfDocument.save(pdfPath.toFile());

            log.info("✅ Fallback PDFBox conversion completed: {}", pdfPath);

        } catch (Exception e) {
            throw new RuntimeException("PDFBox fallback conversion failed: " + e.getMessage(), e);
        }
    }

    private String extractTextFromDocx(Path docxPath) {
        StringBuilder content = new StringBuilder();

        try (XWPFDocument document = new XWPFDocument(Files.newInputStream(docxPath))) {
            for (var paragraph : document.getParagraphs()) {
                String text = paragraph.getText();
                if (text != null && !text.trim().isEmpty()) {
                    content.append(text).append("\n");
                }
            }

            for (var table : document.getTables()) {
                for (var row : table.getRows()) {
                    StringBuilder rowText = new StringBuilder();
                    for (var cell : row.getTableCells()) {
                        String cellText = cell.getText();
                        if (cellText != null && !cellText.trim().isEmpty()) {
                            String cleanCellText = cellText.replace("\t", "    ");
                            rowText.append(cleanCellText).append(" | ");
                        }
                    }
                    if (rowText.length() > 0) {
                        String finalRowText = rowText.toString().trim();
                        if (finalRowText.endsWith("|")) {
                            finalRowText = finalRowText.substring(0, finalRowText.length() - 1).trim();
                        }
                        content.append(finalRowText).append("\n");
                    }
                }
                content.append("\n");
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to extract text from DOCX: " + e.getMessage(), e);
        }

        String extractedText = content.toString();
        extractedText = extractedText.replace("\t", "    ");

        log.info("Extracted {} characters from DOCX", extractedText.length());
        return extractedText;
    }

    private void processTextToPdf(Path templatePath, Path pdfPath, Contract contract) {
        try {
            String templateContent = readFileWithEncoding(templatePath);
            String processedContent = replaceTextVariables(templateContent, contract.getVariableValues());
            createPdfFromText(processedContent, pdfPath, contract);
        } catch (Exception e) {
            throw new RuntimeException("Failed to process text template: " + e.getMessage(), e);
        }
    }

    private void createPdfFromText(String content, Path pdfPath, Contract contract) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDFont font = loadUnicodeFont(document);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                String cleanContent = cleanTextForPdf(content);
                addTextToPage(contentStream, font, cleanContent);
                addSignaturePlaceholdersFromFlow(document, page, font, contract);
            }

            document.save(pdfPath.toFile());
        } catch (Exception e) {
            throw new RuntimeException("Failed to create PDF: " + e.getMessage(), e);
        }
    }

    private String cleanTextForPdf(String text) {
        if (text == null) return "";
        String cleaned = text.replace("\t", "    ");
        cleaned = cleaned.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
        cleaned = new String(cleaned.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        return cleaned;
    }

    private String readFileWithEncoding(Path filePath) {
        List<Charset> charsets = Arrays.asList(
                StandardCharsets.UTF_8,
                StandardCharsets.ISO_8859_1,
                Charset.forName("Windows-1252")
        );

        for (Charset charset : charsets) {
            try {
                return Files.readString(filePath, charset);
            } catch (MalformedInputException e) {
                continue;
            } catch (IOException e) {
                log.warn("Error reading with {}: {}", charset.name(), e.getMessage());
            }
        }

        try {
            byte[] bytes = Files.readAllBytes(filePath);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Cannot read file with any encoding: " + e.getMessage(), e);
        }
    }

    private void addSignaturePlaceholdersFromFlow(PDDocument document, PDPage page, PDFont font, Contract contract) throws IOException {
        if (contract.getFlow() == null || contract.getFlow().getSteps() == null) {
            return;
        }

        try (PDPageContentStream contentStream = new PDPageContentStream(
                document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {

            int yPosition = 150;
            contentStream.setFont(font, 10);

            for (ApprovalStep step : contract.getFlow().getSteps()) {
                if (step.getSignaturePlaceholder() != null && !step.getSignaturePlaceholder().isBlank()) {
                    if (yPosition < 50) break;

                    contentStream.beginText();
                    contentStream.newLineAtOffset(100, yPosition);
                    contentStream.showText(step.getSignaturePlaceholder() + ": _________________________");
                    contentStream.endText();

                    yPosition -= 30;
                }
            }
        }
    }

    private void addTextToPage(PDPageContentStream contentStream, PDFont font, String text) throws IOException {
        String cleanText = text.replace("\t", "    ")
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");

        String[] lines = cleanText.split("\n");
        float yPosition = 750;
        float lineHeight = 15;

        contentStream.beginText();
        contentStream.setFont(font, 10);
        contentStream.newLineAtOffset(50, yPosition);

        for (String line : lines) {
            if (yPosition < 50) {
                break;
            }

            List<String> wrappedLines = wrapText(line, font, 10, 500);
            for (String wrappedLine : wrappedLines) {
                if (yPosition < 50) break;
                String safeLine = wrappedLine.replaceAll("[^\\x20-\\x7E\\x0A]", "?");
                contentStream.showText(safeLine);
                contentStream.newLineAtOffset(0, -lineHeight);
                yPosition -= lineHeight;
            }
        }

        contentStream.endText();
    }

    private List<String> wrapText(String text, PDFont font, float fontSize, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();

        String cleanText = text.replace("\t", "    ")
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");

        String[] words = cleanText.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine + (currentLine.length() > 0 ? " " : "") + word;

            float width;
            try {
                width = font.getStringWidth(testLine) / 1000 * fontSize;
            } catch (IllegalArgumentException e) {
                String safeTestLine = testLine.replaceAll("[^\\x20-\\x7E]", "?");
                width = font.getStringWidth(safeTestLine) / 1000 * fontSize;
            }

            if (width > maxWidth && currentLine.length() > 0) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            } else {
                if (currentLine.length() > 0) currentLine.append(" ");
                currentLine.append(word);
            }
        }

        if (currentLine.length() > 0) lines.add(currentLine.toString());
        return lines;
    }

    private PDFont loadUnicodeFont(PDDocument document) {
        try {
            try {
                ClassPathResource resource = new ClassPathResource("fonts/arial.ttf");
                if (resource.exists()) {
                    try (InputStream fontStream = resource.getInputStream()) {
                        log.info("✅ SUCCESS: Loaded Arial font from classpath");
                        return PDType0Font.load(document, fontStream);
                    }
                }
            } catch (Exception e) {
                log.debug("Không tìm thấy font trong classpath: {}", e.getMessage());
            }

            String[] fontPaths = {
                    "C:/Windows/Fonts/arial.ttf",
                    "C:/Windows/Fonts/times.ttf",
                    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                    "/usr/share/fonts/truetype/freefont/FreeSans.ttf",
                    "/Library/Fonts/Arial.ttf",
                    "/Library/Fonts/Times New Roman.ttf"
            };

            for (String fontPath : fontPaths) {
                File fontFile = new File(fontPath);
                if (fontFile.exists()) {
                    try {
                        PDFont font = PDType0Font.load(document, fontFile);
                        log.info("Loaded font: {}", fontPath);
                        return font;
                    } catch (IOException ex) {
                        log.warn("Cannot load font from {}: {}", fontPath, ex.getMessage());
                    }
                }
            }

            log.warn("Không tìm thấy font Unicode, sử dụng Helvetica");
            return PDType1Font.HELVETICA;

        } catch (Exception e) {
            log.error("Error loading font: {}", e.getMessage());
            return PDType1Font.HELVETICA;
        }
    }

    // ======================== FLOW BUILDER (SINGLE-SOURCE) ========================
    /**
     * Tạo flow mới từ request.signSteps, đảm bảo steps trỏ đúng về flow vừa tạo.
     * Trả về flow đã save (không double-create).
     */
    private ApprovalFlow createApprovalFlow(CreateContractRequest request, ContractTemplate template) {
        ApprovalFlow flow = new ApprovalFlow();
        String flowName = Optional.ofNullable(request.getFlowName())
                .orElse("Luồng ký cho hợp đồng");
        flow.setName(flowName);
        flow.setDescription(request.getFlowDescription());
        // Nếu entity ApprovalFlow có quan hệ với template thì gán (tuỳ model)
        // flow.setTemplate(template);

        LinkedHashSet<ApprovalStep> steps = new LinkedHashSet<>();
        int order = 1;

        for (CreateContractRequest.SignStepRequest s :
                Optional.ofNullable(request.getSignSteps()).orElse(List.of())) {

            ApprovalStep step = new ApprovalStep();
            step.setStepOrder(order++);
            step.setRequired(Boolean.TRUE.equals(s.getRequired()));
            step.setApproverType(s.getApproverType());

            if (ApproverType.USER.equals(s.getApproverType())) {
                Employee approver = userRepository.findById(s.getEmployeeId())
                        .orElseThrow(() -> new RuntimeException("User not found"));
                step.setEmployee(approver);
                step.setPosition(null);
                step.setDepartment(null);
            } else if (ApproverType.POSITION.equals(s.getApproverType())) {
                Position position = positionRepository.findById(s.getPositionId())
                        .orElseThrow(() -> new RuntimeException("Position not found"));
                Department department = departmentRepository.findById(s.getDepartmentId())
                        .orElseThrow(() -> new RuntimeException("Department not found"));
                step.setPosition(position);
                step.setDepartment(department);
                step.setEmployee(null);
            }

            step.setAction(s.getAction());
            step.setSignaturePlaceholder(s.getSignaturePlaceholder());
            step.setIsFinalStep(Boolean.TRUE.equals(s.getIsFinalStep()));

            step.setFlow(flow); // quan trọng: step thuộc về flow này
            steps.add(step);
        }

        flow.setSteps(steps);
        return approvalFlowRepository.save(flow);
    }

    // ======================== READ APIs ========================
    @Override
    @Transactional
    public ContractResponse getById(Long id) {
        Contract contract = contractRepository.findWithVarsById(id)
                .orElseThrow(() -> new RuntimeException("Contract not found"));
        return ContractMapper.toResponse(contract);
    }

    @Transactional
    @Override
    public String previewContract(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        ContractTemplate template = contract.getTemplate();
        if (template == null || template.getFilePath() == null) {
            throw new RuntimeException("Template file not found");
        }

        try {
            Path path = Path.of(template.getFilePath());
            String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
            List<ContractVariableValue> values = variableValueRepository.findByContract_Id(contractId);

            if (fileName.endsWith(".docx")) {
                WordprocessingMLPackage pkg = WordprocessingMLPackage.load(new File(template.getFilePath()));
                replaceDocxVariables(pkg, values);
                return DocxToHtmlConverter.convertToHtml(pkg);
            } else {
                String templateContent = Files.readString(path, StandardCharsets.UTF_8);
                return replaceTextVariables(templateContent, values);
            }

        } catch (Exception e) {
            throw new RuntimeException("Error while preview contract: " + e.getMessage(), e);
        }
    }

    @Transactional
    @Override
    public String previewTemplate(CreateContractRequest request) {
        ContractTemplate template = templateRepository.findById(request.getTemplateId())
                .orElseThrow(() -> new RuntimeException("Template not found"));

        if (template.getFilePath() == null) {
            throw new RuntimeException("Template file path not found");
        }

        try {
            Path path = Path.of(template.getFilePath());
            String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);

            List<ContractVariableValue> values = Optional.ofNullable(request.getVariables()).orElse(List.of())
                    .stream().map(v -> {
                        ContractVariableValue cv = new ContractVariableValue();
                        cv.setVarName(v.getVarName());
                        cv.setVarValue(v.getVarValue());
                        return cv;
                    }).toList();

            if (fileName.endsWith(".docx")) {
                WordprocessingMLPackage pkg = WordprocessingMLPackage.load(new File(template.getFilePath()));
                replaceDocxVariables(pkg, values);
                return DocxToHtmlConverter.convertToHtml(pkg);
            } else {
                String templateContent = Files.readString(path, StandardCharsets.UTF_8);
                return replaceTextVariables(templateContent, values);
            }

        } catch (Exception e) {
            throw new RuntimeException("Error while previewing template: " + e.getMessage(), e);
        }
    }

    @Override
    public List<ContractResponse> getMyContracts(ContractStatus status) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        List<Contract> list = (status == null)
                ? contractRepository.findByCreatedBy_Account_Email(email)
                : contractRepository.findByCreatedBy_Account_EmailAndStatus(email, status);
        return list.stream().map(ContractMapper::toResponse).toList();
    }

    private void replaceDocxVariables(WordprocessingMLPackage pkg, List<ContractVariableValue> values) {
        Map<String, String> map = new HashMap<>();
        for (ContractVariableValue v : values) {
            map.put(v.getVarName(), v.getVarValue() == null ? "" : v.getVarValue());
        }

        List<Text> textNodes = getAllTextElements(pkg.getMainDocumentPart());
        for (Text t : textNodes) {
            String s = t.getValue();
            if (s == null || s.isEmpty()) continue;

            for (Map.Entry<String, String> e : map.entrySet()) {
                String key = e.getKey();
                String val = e.getValue();

                s = s.replace("${" + key + "}", val);
                s = s.replace("{{" + key + "}}", val);
            }
            t.setValue(s);
        }
    }

    private String replaceTextVariables(String content, List<ContractVariableValue> values) {
        if (content == null) return "";
        String result = content;
        for (ContractVariableValue v : values) {
            String key = v.getVarName();
            String val = v.getVarValue() == null ? "" : v.getVarValue();
            result = result.replace("${" + key + "}", val);
            result = result.replace("{{" + key + "}}", val);
        }
        return result;
    }

    private List<Text> getAllTextElements(Object obj) {
        List<Text> texts = new ArrayList<>();
        if (obj == null) return texts;

        if (obj instanceof JAXBElement) {
            obj = ((JAXBElement<?>) obj).getValue();
        }

        if (obj instanceof Text) {
            texts.add((Text) obj);
        } else if (obj instanceof ContentAccessor) {
            List<?> children = ((ContentAccessor) obj).getContent();
            for (Object child : children) {
                texts.addAll(getAllTextElements(child));
            }
        }
        return texts;
    }

    // ======================== UPDATE / OTHER ========================
    @Transactional
    @Override
    public ContractResponse updateContract(Long contractId, CreateContractRequest request) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        if (!contract.getStatus().equals(ContractStatus.DRAFT)
                && !contract.getStatus().equals(ContractStatus.PENDING_APPROVAL)) {
            throw new RuntimeException("Không thể chỉnh sửa hợp đồng trong trạng thái này.");
        }

        contract.setTitle(request.getTitle());

        List<ContractVariableValue> updatedValues = new ArrayList<>();
        for (CreateContractRequest.VariableValueRequest variable : Optional.ofNullable(request.getVariables()).orElse(List.of())) {
            ContractVariableValue cv = variableValueRepository
                    .findByContract_IdAndVarName(contract.getId(), variable.getVarName())
                    .orElse(new ContractVariableValue());
            cv.setContract(contract);
            cv.setVarName(variable.getVarName());
            cv.setVarValue(variable.getVarValue());
            updatedValues.add(cv);
        }
        contract.getVariableValues().clear();
        contract.getVariableValues().addAll(updatedValues);

        variableValueRepository.saveAll(updatedValues);

        // (Tuỳ chọn) cho phép đổi flow khi update draft
        if (request.getFlowId() != null) {
            ApprovalFlow f = approvalFlowRepository.findById(request.getFlowId())
                    .orElseThrow(() -> new RuntimeException("Approval flow not found"));
            contract.setFlow(f);
        } else if ("existing".equalsIgnoreCase(Optional.ofNullable(request.getFlowOption()).orElse(""))
                && request.getExistingFlowId() != null) {
            ApprovalFlow f = approvalFlowRepository.findById(request.getExistingFlowId())
                    .orElseThrow(() -> new RuntimeException("Approval flow not found"));
            contract.setFlow(f);
        } else if ("new".equalsIgnoreCase(Optional.ofNullable(request.getFlowOption()).orElse(""))
                && request.getSignSteps() != null && !request.getSignSteps().isEmpty()) {
            ApprovalFlow f = createApprovalFlow(request, contract.getTemplate());
            contract.setFlow(f);
        }

        contractRepository.save(contract);
        return ContractMapper.toResponse(contract);
    }

    @Transactional
    public void changeApprover(Long contractId, Long stepId, Long newApproverId, boolean isUserApprover) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        ContractApproval contractApproval = contractApprovalRepository.findByContract_IdAndStep_Id(contractId, stepId)
                .orElseThrow(() -> new RuntimeException("Approval step not found"));

        if (contractApproval.getStatus() == ApprovalStatus.APPROVED || contractApproval.getStatus() == ApprovalStatus.REJECTED) {
            throw new RuntimeException("Không thể thay đổi người ký vì bước phê duyệt này đã được quyết định.");
        }

        if (isUserApprover) {
            if (contractApproval.getPosition() != null) {
                contractApproval.setPosition(null);
                Employee newUser = userRepository.findById(newApproverId)
                        .orElseThrow(() -> new RuntimeException("User not found"));
                contractApproval.setApprover(newUser);
                contractApproval.setDepartment(null);
            } else {
                throw new RuntimeException("Current approver is not a Position, cannot change to User.");
            }
        } else {
            if (contractApproval.getApprover() != null) {
                contractApproval.setApprover(null);
                // NOTE: phương thức này đang nhận 1 id cho cả position & department là chưa chuẩn,
                // giữ nguyên theo API hiện tại để không phá backward-compat.
                Position newPosition = positionRepository.findById(newApproverId)
                        .orElseThrow(() -> new RuntimeException("Position not found"));
                Department newDepartment = departmentRepository.findById(newApproverId)
                        .orElseThrow(() -> new RuntimeException("Department not found"));
                contractApproval.setPosition(newPosition);
                contractApproval.setDepartment(newDepartment);
            } else {
                throw new RuntimeException("Current approver is not a User, cannot change to Position.");
            }
        }

        contractApprovalRepository.save(contractApproval);
    }

    @Transactional
    @Override
    public void cancelContract(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        if (contract.getStatus() == ContractStatus.PENDING_APPROVAL) {
            List<ContractApproval> contractApprovals = contractApprovalRepository.findByContract(contract);
            for (ContractApproval approval : contractApprovals) {
                approval.setStatus(ApprovalStatus.CANCELLED);
                contractApprovalRepository.save(approval);
            }
        } else {
            throw new RuntimeException("Không thể hủy hợp đồng vì hợp đồng không phải trong trạng thái trình ký.");
        }

        contract.setStatus(ContractStatus.CANCELLED);
        contractRepository.save(contract);
    }

    @Transactional
    @Override
    public void deleteContract(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        if (!contract.getStatus().equals(ContractStatus.DRAFT)) {
            throw new RuntimeException("Không thể xóa hợp đồng vì hợp đồng không ở trạng thái DRAFT.");
        }

        contractRepository.delete(contract);
    }

    @Scheduled(fixedRate = 60 * 60 * 1000) // mỗi giờ
    public void cleanupPreviewTemp() {
        Path root = Paths.get("uploads", "previews");
        try {
            if (!Files.exists(root)) return;
            long expire = System.currentTimeMillis() - 2 * 60 * 60 * 1000; // 2h
            try (var dir = Files.list(root)) {
                dir.forEach(dirPath -> {
                    try {
                        if (Files.getLastModifiedTime(dirPath).toMillis() < expire) {
                            Files.walk(dirPath).sorted(Comparator.reverseOrder()).forEach(p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (Exception ignore) {
                                }
                            });
                        }
                    } catch (Exception ignore) {
                    }
                });
            }
        } catch (Exception e) {
            log.warn("cleanupPreviewTemp error: {}", e.getMessage());
        }
    }
}
