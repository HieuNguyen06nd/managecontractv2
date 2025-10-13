package com.hieunguyen.ManageContract.service.impl;

import com.hieunguyen.ManageContract.common.constants.*;
import com.hieunguyen.ManageContract.dto.approval.ApprovalStepResponse;
import com.hieunguyen.ManageContract.dto.approval.StepApprovalRequest;
import com.hieunguyen.ManageContract.dto.contract.ContractResponse;
import com.hieunguyen.ManageContract.dto.contractSign.SignStepRequest;
import com.hieunguyen.ManageContract.entity.*;
import com.hieunguyen.ManageContract.mapper.ContractMapper;
import com.hieunguyen.ManageContract.repository.*;
import com.hieunguyen.ManageContract.security.jwt.SecurityUtil;
import com.hieunguyen.ManageContract.service.ContractApprovalService;
import com.hieunguyen.ManageContract.service.ContractFileService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

import static org.apache.fop.svg.SVGUtilities.wrapText;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContractApprovalServiceImpl implements ContractApprovalService {

    private final ContractRepository contractRepository;
    private final UserRepository userRepository;
    private final ApprovalFlowRepository flowRepository;
    private final ContractApprovalRepository contractApprovalRepository;
    private final ContractSignatureRepository contractSignatureRepository;
    private final ContractFileService contractFileService;
    private final SecurityUtil securityUtils;

    @Transactional
    @Override
    public ContractResponse submitForApproval(Long contractId, Long flowId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        if (contract.getStatus() != ContractStatus.DRAFT) {
            throw new RuntimeException("Only draft contracts can be submitted for approval");
        }

        if (contractApprovalRepository.existsByContractId(contractId)) {
            throw new RuntimeException("Contract already has an approval flow");
        }

        // Chọn flow
        ApprovalFlow flow = determineApprovalFlow(contract, flowId);

        if (flow.getSteps() == null || flow.getSteps().isEmpty()) {
            throw new RuntimeException("Selected flow has no steps");
        }

        // Snapshot step sang ContractApproval
        List<ContractApproval> approvals = copyFlowToContractApproval(contract, flow);
        contractApprovalRepository.saveAll(approvals);

        // ĐẢM BẢO FILE HỢP ĐỒNG TỒN TẠI TRƯỚC KHI VALIDATE
        ensureContractFileExists(contract);

        // VALIDATE PLACEHOLDERS - XỬ LÝ MỀM MẠI
        try {
            boolean isValid = contractFileService.validatePlaceholdersInContract(contractId);
            if (!isValid) {
                log.warn("Some signature placeholders not found in contract file for contract {}. " +
                        "This may cause issues during signing process.", contractId);
                // Không throw exception, chỉ log cảnh báo
            }
        } catch (Exception e) {
            log.warn("Placeholder validation failed for contract {}: {}. Continuing anyway.", contractId, e.getMessage());
            // Tiếp tục xử lý dù validate thất bại
        }

        contract.setStatus(ContractStatus.PENDING_APPROVAL);
        contract.setFlow(flow);

        return ContractMapper.toResponse(contractRepository.save(contract));
    }

    /**
     * Xác định flow để sử dụng khi trình ký
     */
    private ApprovalFlow determineApprovalFlow(Contract contract, Long flowId) {
        // Ưu tiên flow được chọn trực tiếp
        if (flowId != null) {
            return flowRepository.findById(flowId)
                    .orElseThrow(() -> new RuntimeException("Flow not found"));
        }

        // Sau đó đến flow đã được gán cho contract
        if (contract.getFlow() != null) {
            return contract.getFlow();
        }

        // Cuối cùng là flow mặc định của template
        return Optional.ofNullable(contract.getTemplate().getDefaultFlow())
                .orElseThrow(() -> new RuntimeException("No approval flow available"));
    }

    /**
     * Copy flow sang ContractApproval
     */
    private List<ContractApproval> copyFlowToContractApproval(Contract contract, ApprovalFlow flow) {
        return flow.getSteps().stream()
                .map(step -> ContractApproval.builder()
                        .contract(contract)
                        .step(step)
                        .stepOrder(step.getStepOrder())
                        .required(step.getRequired())
                        .isFinalStep(step.getIsFinalStep())
                        .department(step.getApproverType() == ApproverType.POSITION ? step.getDepartment() : null)
                        .position(step.getApproverType() == ApproverType.POSITION ? step.getPosition() : null)
                        .isCurrent(step.getStepOrder() == 1)
                        .status(ApprovalStatus.PENDING)
                        .signaturePlaceholder(step.getSignaturePlaceholder())
                        .build())
                .toList();
    }

    /**
     * Đảm bảo file hợp đồng tồn tại
     */
    private void ensureContractFileExists(Contract contract) {
        try {
            boolean fileExists = contract.getFilePath() != null &&
                    Files.exists(Path.of(contract.getFilePath()));

            if (!fileExists) {
                log.info("Contract file not found, generating new file for contract: {}", contract.getId());

                // Tạo file mới bằng ContractService
                String filePath = generateNewContractFile(contract);
                contract.setFilePath(filePath);
                contractRepository.save(contract);

                log.info("Successfully created contract file: {}", filePath);
            } else {
                log.info("Contract file already exists: {}", contract.getFilePath());
            }

        } catch (Exception e) {
            log.error("CRITICAL: Could not create contract file for contract {}: {}",
                    contract.getId(), e.getMessage());
            throw new RuntimeException("Could not create contract file: " + e.getMessage(), e);
        }
    }

    private String generateNewContractFile(Contract contract) {
        try {
            ContractTemplate template = contract.getTemplate();
            if (template == null || template.getFilePath() == null) {
                throw new RuntimeException("Template not found");
            }

            Path templatePath = Path.of(template.getFilePath());
            if (!Files.exists(templatePath)) {
                throw new RuntimeException("Template file does not exist: " + template.getFilePath());
            }

            // Tạo thư mục contract
            Path contractDir = Paths.get("uploads", "contracts", String.valueOf(contract.getId()));
            Files.createDirectories(contractDir);

            Path pdfPath = contractDir.resolve("contract_" + System.currentTimeMillis() + ".pdf");

            // Xử lý dựa trên định dạng file template
            String fileName = templatePath.getFileName().toString().toLowerCase();

            if (fileName.endsWith(".docx")) {
                processDocxToPdf(templatePath, pdfPath, contract);
            } else {
                processTextToPdf(templatePath, pdfPath, contract);
            }

            return pdfPath.toString();

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate contract file: " + e.getMessage(), e);
        }
    }


    private void processDocxToPdf(Path templatePath, Path pdfPath, Contract contract) {
        try {
            // Đọc nội dung DOCX
            String content = extractTextFromDocx(templatePath);

            // Thay thế biến
            String processedContent = replaceTextVariables(content, contract.getVariableValues());

            // Tạo PDF
            createPdfFromText(processedContent, pdfPath, contract);

        } catch (Exception e) {
            throw new RuntimeException("Failed to process DOCX template: " + e.getMessage(), e);
        }
    }

    private void createPdfFromText(String content, Path pdfPath, Contract contract) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDFont font = loadUnicodeFont(document);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                // Thêm nội dung hợp đồng
                addTextToPage(contentStream, font, content);

                // Thêm các placeholder chữ ký từ flow
                addSignaturePlaceholdersFromFlow(document, page, font, contract);
            }

            document.save(pdfPath.toFile());
        } catch (Exception e) {
            throw new RuntimeException("Failed to create PDF: " + e.getMessage(), e);
        }
    }
    private void processTextToPdf(Path templatePath, Path pdfPath, Contract contract) {
        try {
            // Đọc file với encoding linh hoạt
            String templateContent = readFileWithEncoding(templatePath);
            String processedContent = replaceTextVariables(templateContent, contract.getVariableValues());

            // Tạo PDF
            createPdfFromText(processedContent, pdfPath, contract);

        } catch (Exception e) {
            throw new RuntimeException("Failed to process text template: " + e.getMessage(), e);
        }
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
                // Thử encoding tiếp theo
                continue;
            } catch (IOException e) {
                log.warn("Error reading with {}: {}", charset.name(), e.getMessage());
            }
        }

        // Fallback
        try {
            byte[] bytes = Files.readAllBytes(filePath);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Cannot read file with any encoding: " + e.getMessage(), e);
        }
    }

    private String extractTextFromDocx(Path docxPath) {
        StringBuilder content = new StringBuilder();

        try (XWPFDocument document = new XWPFDocument(Files.newInputStream(docxPath))) {

            // Đọc các đoạn văn
            for (var paragraph : document.getParagraphs()) {
                String text = paragraph.getText();
                if (text != null && !text.trim().isEmpty()) {
                    content.append(text).append("\n");
                }
            }

            // Đọc các bảng
            for (var table : document.getTables()) {
                for (var row : table.getRows()) {
                    StringBuilder rowText = new StringBuilder();
                    for (var cell : row.getTableCells()) {
                        String cellText = cell.getText();
                        if (cellText != null && !cellText.trim().isEmpty()) {
                            rowText.append(cellText).append("\t");
                        }
                    }
                    if (rowText.length() > 0) {
                        content.append(rowText.toString().trim()).append("\n");
                    }
                }
                content.append("\n");
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to extract text from DOCX: " + e.getMessage(), e);
        }

        log.info("Extracted {} characters from DOCX", content.length());
        return content.toString();
    }



    private PDFont loadUnicodeFont(PDDocument document) {
        try {
            // Thử load font từ hệ thống
            String[] fontPaths = {
                    "C:/Windows/Fonts/arial.ttf",
                    "/usr/share/fonts/truetype/freefont/FreeSans.ttf",
                    "/Library/Fonts/Arial.ttf"
            };

            for (String fontPath : fontPaths) {
                File fontFile = new File(fontPath);
                if (fontFile.exists()) {
                    return PDType0Font.load(document, fontFile);
                }
            }

            // Fallback to Helvetica
            return PDType1Font.HELVETICA;
        } catch (Exception e) {
            log.warn("Could not load Unicode font, using Helvetica: {}", e.getMessage());
            return PDType1Font.HELVETICA;
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
                    if (yPosition < 50) break; // Hết chỗ trên trang

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
        String[] lines = text.split("\n");
        float yPosition = 750;
        float lineHeight = 15;

        contentStream.beginText();
        contentStream.setFont(font, 10);
        contentStream.newLineAtOffset(50, yPosition);

        for (String line : lines) {
            if (yPosition < 50) {
                break; // Hết trang
            }

            // Xử lý text dài (cắt thành nhiều dòng nếu cần)
            List<String> wrappedLines = wrapText(line, font, 10, 500);
            for (String wrappedLine : wrappedLines) {
                if (yPosition < 50) break;

                contentStream.showText(wrappedLine);
                contentStream.newLineAtOffset(0, -lineHeight);
                yPosition -= lineHeight;
            }
        }

        contentStream.endText();
    }

    private List<String> wrapText(String text, PDFont font, float fontSize, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine + (currentLine.length() > 0 ? " " : "") + word;
            float width = font.getStringWidth(testLine) / 1000 * fontSize;

            if (width > maxWidth && currentLine.length() > 0) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            } else {
                if (currentLine.length() > 0) {
                    currentLine.append(" ");
                }
                currentLine.append(word);
            }
        }

        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        return lines;
    }
    // Thêm các phương thức helper cần thiết
    private String replaceTextVariables(String content, List<ContractVariableValue> values) {
        if (content == null) return "";
        String result = content;
        for (ContractVariableValue v : values) {
            String key = v.getVarName();
            String val = v.getVarValue() != null ? v.getVarValue() : "";
            result = result.replace("${" + key + "}", val);
            result = result.replace("{{" + key + "}}", val);
        }
        return result;
    }

    @Transactional
    @Override
    public ContractResponse signStep(Long contractId, Long stepId, SignStepRequest req) {
        ContractApproval approval = contractApprovalRepository.findById(stepId)
                .orElseThrow(() -> new RuntimeException("Approval step not found"));

        Contract contract = approval.getContract();
        if (!contract.getId().equals(contractId)) {
            throw new RuntimeException("Step không thuộc hợp đồng này");
        }
        if (!Boolean.TRUE.equals(approval.getIsCurrent())) {
            throw new RuntimeException("Step chưa đến lượt ký");
        }

        // Kiểm tra placeholder
        if (approval.getSignaturePlaceholder() == null || approval.getSignaturePlaceholder().isBlank()) {
            throw new RuntimeException("No signature placeholder defined for this approval step");
        }

        // Chỉ cho phép ký khi step có yêu cầu ký
        ApprovalAction action = approval.getStep().getAction();
        if (action == ApprovalAction.APPROVE_ONLY) {
            throw new RuntimeException("Bước này chỉ phê duyệt, không yêu cầu ký.");
        }

        // 1) Lấy nhân sự hiện tại
        String email = securityUtils.getCurrentUserEmail();
        Employee me = userRepository.findByAccount_Email(email)
                .orElseThrow(() -> new RuntimeException("Employee not found: " + email));

        // 2) Kiểm tra quyền theo approverType
        validateApprovalPermission(approval.getStep(), me);

        // 3) LẤY CHỮ KÝ TỪ REQUEST HOẶC EMPLOYEE
        String signatureUrl = req.getSignatureImage(); // ✅ ƯU TIÊN CHỮ KÝ TỪ REQUEST

        if (signatureUrl == null || signatureUrl.isEmpty()) {
            // Fallback: lấy từ employee
            signatureUrl = me.getSignatureImage();
            log.info("Using employee signature from database");
        } else {
            log.info("Using signature from request");
        }

        if (signatureUrl == null || signatureUrl.isEmpty()) {
            throw new RuntimeException("Bạn chưa có chữ ký số. Vui lòng upload chữ ký trước.");
        }

        // 4) Chèn chữ ký vào file THEO PLACEHOLDER CỦA APPROVAL
        String updatedPath = contractFileService.embedSignatureForApproval(
                contract.getId(),
                signatureUrl,
                approval.getId()
        );

        if (updatedPath != null) {
            contract.setFilePath(updatedPath);
            contractRepository.save(contract);
        }

        // 5) Lưu snapshot chữ ký
        ContractSignature signature = new ContractSignature();
        signature.setContract(contract);
        signature.setSigner(me);
        signature.setApprovalStep(approval);
        signature.setSignedAt(LocalDateTime.now());
        signature.setSignatureImage(signatureUrl);
        signature.setPlaceholderKey(approval.getSignaturePlaceholder());
        signature.setType(SignatureType.EMPLOYEE);
        contractSignatureRepository.save(signature);

        // 6) Hoàn tất bước nếu là SIGN_ONLY
        if (action == ApprovalAction.SIGN_ONLY) {
            completeApprovalStep(approval, me, req.getComment());

            if (Boolean.TRUE.equals(approval.getIsFinalStep())) {
                contract.setStatus(ContractStatus.APPROVED);
                contractRepository.save(contract);
                return ContractMapper.toResponse(contract);
            }

            // Chuyển sang step tiếp theo
            moveToNextStep(contract, approval.getStepOrder());
        }

        return ContractMapper.toResponse(contract);
    }

    @Transactional
    @Override
    public ContractResponse approveStep(Long stepId, StepApprovalRequest request) {
        return processStep(stepId, request, true);
    }

    @Transactional
    @Override
    public ContractResponse rejectStep(Long stepId, StepApprovalRequest request) {
        return processStep(stepId, request, false);
    }

    private ContractResponse processStep(Long stepId, StepApprovalRequest request, boolean approved) {
        ContractApproval approval = contractApprovalRepository.findById(stepId)
                .orElseThrow(() -> new RuntimeException("Approval step not found"));

        if (!Boolean.TRUE.equals(approval.getIsCurrent())) {
            throw new RuntimeException("This step is not active for approval");
        }

        // Lấy Employee hiện tại
        String email = securityUtils.getCurrentUserEmail();
        Employee me = userRepository.findByAccount_Email(email)
                .orElseThrow(() -> new RuntimeException("Employee not found for email: " + email));

        // Kiểm tra quyền
        validateApprovalPermission(approval.getStep(), me);

        // Cập nhật kết quả duyệt
        approval.setApprover(me);
        approval.setApprovedAt(LocalDateTime.now());
        approval.setComment(request.getComment());
        approval.setIsCurrent(false);
        approval.setStatus(approved ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED);
        contractApprovalRepository.save(approval);

        Contract contract = approval.getContract();

        if (!approved) {
            contract.setStatus(ContractStatus.REJECTED);
            contractRepository.save(contract);
            return ContractMapper.toResponse(contract);
        }

        // Xử lý khi APPROVE: thêm thông tin phê duyệt vào PDF
        ApprovalAction action = approval.getStep().getAction();
        if (action == ApprovalAction.APPROVE_ONLY || action == ApprovalAction.SIGN_THEN_APPROVE) {
            String approveText = String.format(
                    "Đã phê duyệt bởi: %s - %s - %s",
                    me.getFullName() != null ? me.getFullName() : me.getAccount().getEmail(),
                    me.getPhone() != null ? me.getPhone() : "Chưa cập nhật SĐT",
                    LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
            );

            contractFileService.addApprovalText(contract.getFilePath(), approveText);
        }

        if (Boolean.TRUE.equals(approval.getIsFinalStep())) {
            contract.setStatus(ContractStatus.APPROVED);
            contractRepository.save(contract);
            return ContractMapper.toResponse(contract);
        }

        // Chuyển sang step tiếp theo
        moveToNextStep(contract, approval.getStepOrder());

        return ContractMapper.toResponse(contract);
    }

    /**
     * Kiểm tra quyền phê duyệt/chữ ký
     */
    private void validateApprovalPermission(ApprovalStep step, Employee employee) {
        if (step.getApproverType() == null) {
            throw new RuntimeException("Step approverType is not set");
        }

        switch (step.getApproverType()) {
            case USER -> {
                if (step.getEmployee() == null || !step.getEmployee().getId().equals(employee.getId())) {
                    throw new RuntimeException("Bạn không phải người được chỉ định duyệt/ký bước này");
                }
            }
            case POSITION -> {
                if (step.getDepartment() == null || step.getPosition() == null) {
                    throw new RuntimeException("Step thiếu department/position yêu cầu");
                }
                if (employee.getDepartment() == null || employee.getPosition() == null
                        || !step.getDepartment().getId().equals(employee.getDepartment().getId())
                        || !step.getPosition().getId().equals(employee.getPosition().getId())) {
                    throw new RuntimeException("Bạn không đúng vị trí/phòng ban yêu cầu để duyệt/ký bước này");
                }
            }
            default -> throw new RuntimeException("approverType không hỗ trợ");
        }
    }

    /**
     * Hoàn thành bước phê duyệt
     */
    private void completeApprovalStep(ContractApproval approval, Employee approver, String comment) {
        approval.setApprover(approver);
        approval.setApprovedAt(LocalDateTime.now());
        approval.setComment(comment);
        approval.setIsCurrent(false);
        approval.setStatus(ApprovalStatus.APPROVED);
        contractApprovalRepository.save(approval);
    }

    /**
     * Chuyển sang bước tiếp theo
     */
    private void moveToNextStep(Contract contract, Integer currentStepOrder) {
        contractApprovalRepository.findByContractIdAndStepOrder(
                contract.getId(), currentStepOrder + 1
        ).ifPresentOrElse(next -> {
            next.setIsCurrent(true);
            contractApprovalRepository.save(next);
            contract.setStatus(ContractStatus.PENDING_APPROVAL);
            contractRepository.save(contract);
        }, () -> {
            throw new RuntimeException("Next step not found");
        });
    }

    @Override
    public List<ContractResponse> getMyHandledContracts(ContractStatus status) {
        String email = securityUtils.getCurrentUserEmail();
        Employee me = userRepository.findByAccount_Email(email)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        List<Contract> contracts = contractApprovalRepository
                .findAllByApproverIdAndContract_Status(me.getId(), status);

        return contracts.stream()
                .map(ContractMapper::toResponse)
                .toList();
    }

    @Override
    public List<ContractResponse> getMyPendingContracts() {
        String email = securityUtils.getCurrentUserEmail();
        Employee me = userRepository.findByAccount_Email(email)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        List<ContractApproval> approvals = contractApprovalRepository
                .findAllByIsCurrentTrueAndStatusAndContract_Status(
                        ApprovalStatus.PENDING,
                        ContractStatus.PENDING_APPROVAL
                );

        return approvals.stream()
                .filter(a -> {
                    var step = a.getStep();
                    return switch (step.getApproverType()) {
                        case USER -> step.getEmployee() != null && step.getEmployee().getId().equals(me.getId());
                        case POSITION -> step.getDepartment() != null && step.getPosition() != null
                                && me.getDepartment() != null && me.getPosition() != null
                                && step.getDepartment().getId().equals(me.getDepartment().getId())
                                && step.getPosition().getId().equals(me.getPosition().getId());
                    };
                })
                .map(a -> {
                    ContractResponse dto = ContractMapper.toResponse(a.getContract());
                    dto.setCurrentStepId(a.getId());
                    dto.setCurrentStepName(buildCurrentStepName(a.getStep()));
                    dto.setCurrentStepAction(a.getStep().getAction().name());
                    dto.setCurrentStepSignaturePlaceholder(a.getSignaturePlaceholder());
                    return dto;
                })
                .toList();
    }

    private String buildCurrentStepName(ApprovalStep step) {
        if (step == null || step.getApproverType() == null) return "Bước hiện tại";
        return switch (step.getApproverType()) {
            case USER -> {
                var emp = step.getEmployee();
                yield emp != null
                        ? ("Người duyệt: " + (emp.getFullName() != null ? emp.getFullName() : emp.getAccount().getEmail()))
                        : "Người duyệt (chưa gán)";
            }
            case POSITION -> {
                String dept = step.getDepartment() != null ? step.getDepartment().getName() : "Phòng/ban?";
                String pos  = step.getPosition()   != null ? step.getPosition().getName()   : "Chức vụ?";
                yield "Vị trí: " + dept + " - " + pos;
            }
        };
    }

    @Override
    public ContractResponse getApprovalProgress(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        ContractResponse dto = ContractMapper.toResponse(contract);

        contractApprovalRepository.findByContractIdAndIsCurrentTrue(contractId)
                .ifPresent(a -> {
                    dto.setCurrentStepId(a.getId());
                    dto.setCurrentStepName(buildCurrentStepName(a.getStep()));
                    dto.setCurrentStepSignaturePlaceholder(a.getSignaturePlaceholder());
                });

        return dto;
    }

    @Override
    public ContractResponse getApprovalProgressOrPreview(Long contractId, Long flowId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        List<ContractApproval> approvals =
                contractApprovalRepository.findAllByContractIdOrderByStepOrderAsc(contractId);

        ContractResponse dto = ContractMapper.toResponse(contract);

        if (!approvals.isEmpty()) {
            dto.setHasFlow(true);
            dto.setFlowSource("CONTRACT");
            dto.setFlowId(approvals.get(0).getStep().getFlow().getId());
            dto.setFlowName(approvals.get(0).getStep().getFlow().getName());

            List<ApprovalStepResponse> steps = approvals.stream().map(a -> {
                ApprovalStep s = a.getStep();
                ApprovalStepResponse r = ApprovalStepResponse.builder()
                        .id(a.getId())
                        .stepOrder(a.getStepOrder())
                        .required(a.getRequired())
                        .approverType(s.getApproverType())
                        .isFinalStep(a.getIsFinalStep())
                        .employeeId(s.getEmployee() != null ? s.getEmployee().getId() : null)
                        .employeeName(s.getEmployee() != null
                                ? (s.getEmployee().getFullName() != null ? s.getEmployee().getFullName()
                                : s.getEmployee().getAccount().getEmail())
                                : null)
                        .positionId(s.getPosition() != null ? s.getPosition().getId() : null)
                        .positionName(s.getPosition() != null ? s.getPosition().getName() : null)
                        .departmentId(s.getDepartment() != null ? s.getDepartment().getId() : null)
                        .departmentName(s.getDepartment() != null ? s.getDepartment().getName() : null)
                        .action(s.getAction())
                        .signaturePlaceholder(a.getSignaturePlaceholder())
                        .status(a.getStatus())
                        .isCurrent(a.getIsCurrent())
                        .decidedBy(a.getApprover() != null
                                ? (a.getApprover().getFullName() != null ? a.getApprover().getFullName()
                                : a.getApprover().getAccount().getEmail())
                                : null)
                        .decidedAt(a.getApprovedAt() != null ? a.getApprovedAt().toString() : null)
                        .build();
                return r;
            }).toList();

            dto.setSteps(steps);

            contractApprovalRepository.findByContractIdAndIsCurrentTrue(contractId)
                    .ifPresent(a -> {
                        dto.setCurrentStepId(a.getId());
                        dto.setCurrentStepName(buildCurrentStepName(a.getStep()));
                        dto.setCurrentStepAction(a.getStep().getAction().name());
                        dto.setCurrentStepSignaturePlaceholder(a.getSignaturePlaceholder());
                    });
            return dto;
        }

        // Preview flow
        ApprovalFlow flow;
        if (flowId != null) {
            flow = flowRepository.findById(flowId)
                    .orElseThrow(() -> new RuntimeException("Flow not found"));
            if (!flow.getTemplate().getId().equals(contract.getTemplate().getId())) {
                throw new RuntimeException("Flow không thuộc template của hợp đồng");
            }
            dto.setFlowSource("SELECTED");
        } else {
            flow = Optional.ofNullable(contract.getTemplate().getDefaultFlow())
                    .orElseThrow(() -> new RuntimeException("Template chưa có flow mặc định"));
            dto.setFlowSource("TEMPLATE_DEFAULT");
        }

        dto.setHasFlow(false);
        dto.setFlowId(flow.getId());
        dto.setFlowName(flow.getName());

        List<ApprovalStepResponse> previewSteps = flow.getSteps().stream()
                .sorted(Comparator.comparingInt(ApprovalStep::getStepOrder))
                .map(s -> ApprovalStepResponse.builder()
                        .id(s.getId())
                        .stepOrder(s.getStepOrder())
                        .required(s.getRequired())
                        .approverType(s.getApproverType())
                        .isFinalStep(s.getIsFinalStep())
                        .employeeId(s.getEmployee() != null ? s.getEmployee().getId() : null)
                        .employeeName(s.getEmployee() != null
                                ? (s.getEmployee().getFullName() != null ? s.getEmployee().getFullName()
                                : s.getEmployee().getAccount().getEmail())
                                : null)
                        .positionId(s.getPosition() != null ? s.getPosition().getId() : null)
                        .positionName(s.getPosition() != null ? s.getPosition().getName() : null)
                        .departmentId(s.getDepartment() != null ? s.getDepartment().getId() : null)
                        .departmentName(s.getDepartment() != null ? s.getDepartment().getName() : null)
                        .action(s.getAction())
                        .signaturePlaceholder(s.getSignaturePlaceholder())
                        .build()
                ).toList();

        dto.setSteps(previewSteps);
        return dto;
    }

    @Override
    public String getEmployeeSignature(Long employeeId) {
        Employee employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        if (employee.getSignatureImage() == null || employee.getSignatureImage().isEmpty()) {
            throw new RuntimeException("Employee does not have a signature");
        }

        return employee.getSignatureImage();
    }
}