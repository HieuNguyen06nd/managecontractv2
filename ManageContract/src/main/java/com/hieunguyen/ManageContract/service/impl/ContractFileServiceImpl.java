package com.hieunguyen.ManageContract.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hieunguyen.ManageContract.entity.Contract;
import com.hieunguyen.ManageContract.entity.ContractApproval;
import com.hieunguyen.ManageContract.entity.ContractVariableValue;
import com.hieunguyen.ManageContract.entity.ApprovalStep;
import com.hieunguyen.ManageContract.repository.ContractApprovalRepository;
import com.hieunguyen.ManageContract.repository.ContractRepository;
import com.hieunguyen.ManageContract.service.ContractFileService;
import jakarta.xml.bind.JAXBElement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.docx4j.model.datastorage.migration.VariablePrepare;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.BinaryPartAbstractImage;
import org.docx4j.wml.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ContractFileServiceImpl implements ContractFileService {

    private static final ObjectFactory WML = new ObjectFactory();
    private static final String UPLOAD_ROOT = "uploads/contracts";
    private static final String DOCX_NAME = "contract.docx";
    private static final String PDF_NAME  = "contract.pdf";

    // chữ ký ảnh mặc định
    private static final float DEFAULT_SIG_W = 180f;
    private static final float DEFAULT_SIG_H = 60f;

    private final ContractRepository contractRepository;
    private final ContractApprovalRepository contractApprovalRepository;

    // OnlyOffice & chữ ký
    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper om = new ObjectMapper();

    @Value("${app.ds.url:http://localhost:8081}")
    private String docServer; // http://localhost:8081 (Document Server)

    @Value("${app.ds.source-base:http://host.docker.internal:8080}")
    private String hostBaseUrl; // http://host.docker.internal:8080 (BE base URL để DS gọi vào)

    @Value("${app.signature.storage-dir:uploads/signatures}")
    private String signatureStorageDir;

    // ========================================================================
    // ContractFileService API
    // ========================================================================

    /**
     * Tạo file hợp đồng (DOCX từ template + variables) rồi convert PDF bằng OnlyOffice
     */
    @Override
    public String generateContractFile(Contract contract) {
        try {
            Path docx = ensureDocxGenerated(contract, /*overrideVars*/ null);
            Path pdf  = pdfPathOf(contract.getId());

            convertToPdfUsingOnlyOffice(contract.getId(), docx, pdf);

            contract.setFilePath(pdf.toString());
            contract.setFileGeneratedAt(LocalDateTime.now());
            contractRepository.save(contract);

            return pdf.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate contract file: " + e.getMessage(), e);
        }
    }

    /**
     * Tạo file hợp đồng với danh sách biến truyền vào (ưu tiên biến tham số nếu có)
     */
    @Override
    public String generateContractFileWithVariables(Contract contract, List<ContractVariableValue> variableValues) {
        try {
            Path docx = ensureDocxGenerated(contract, variableValues);
            Path pdf  = pdfPathOf(contract.getId());

            convertToPdfUsingOnlyOffice(contract.getId(), docx, pdf);

            contract.setFilePath(pdf.toString());
            contract.setFileGeneratedAt(LocalDateTime.now());
            contractRepository.save(contract);

            return pdf.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate contract file (with variables): " + e.getMessage(), e);
        }
    }

    /**
     * Lấy file gốc để tải xuống:
     * - Nếu PDF đã sinh -> trả PDF
     * - Nếu chưa có PDF -> trả DOCX (nếu có)
     */
    @Override
    public File getContractFile(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        Path pdf  = pdfPathOf(contractId);
        Path docx = docxPathOf(contractId);

        if (Files.exists(pdf)) return pdf.toFile();
        if (Files.exists(docx)) return docx.toFile();

        // fallback: nếu DB có filePath và tồn tại thì trả
        if (contract.getFilePath() != null && Files.exists(Paths.get(contract.getFilePath()))) {
            return Paths.get(contract.getFilePath()).toFile();
        }

        throw new RuntimeException("Contract file not generated yet");
    }

    /**
     * Ký bằng ảnh: thay thế placeholder trong DOCX => convert lại PDF (OnlyOffice)
     * @param filePath có thể là đường dẫn PDF hoặc DOCX hiện tại; dùng để suy ra contractId
     */
    @Override
    public String embedSignature(String filePath, String imageUrl, String placeholder) {
        try {
            if (placeholder == null || placeholder.isBlank()) {
                throw new RuntimeException("Placeholder is required");
            }

            Long contractId = extractContractIdFromPath(Paths.get(filePath));

            // Bảo đảm đã có DOCX
            Path docx = ensureDocxGenerated(
                    contractRepository.findById(contractId).orElseThrow(), null);

            // Chèn ảnh vào DOCX
            byte[] imageBytes = loadImageBytes(imageUrl);
            if (imageBytes == null || imageBytes.length == 0) {
                throw new RuntimeException("Signature image is empty or invalid");
            }

            WordprocessingMLPackage pkg = WordprocessingMLPackage.load(docx.toFile());
            boolean ok = replacePlaceholderWithImage(pkg, normalizePlaceholder(placeholder), imageBytes, DEFAULT_SIG_W, DEFAULT_SIG_H);
            if (!ok) {
                throw new RuntimeException("Placeholder not found in DOCX: " + placeholder);
            }
            pkg.save(docx.toFile());

            // Convert lại PDF
            Path pdf = pdfPathOf(contractId);
            convertToPdfUsingOnlyOffice(contractId, docx, pdf);

            // Cập nhật DB
            Contract c = contractRepository.findById(contractId).orElseThrow();
            c.setFilePath(pdf.toString());
            c.setFileGeneratedAt(LocalDateTime.now());
            contractRepository.save(c);

            return pdf.toString();
        } catch (Exception e) {
            throw new RuntimeException("Embed signature (OnlyOffice) failed: " + e.getMessage(), e);
        }
    }

    /**
     * Ký theo step phê duyệt – dùng placeholder của step
     */
    @Override
    public String embedSignatureForApproval(Long contractId, String imageUrl, Long approvalId) {
        try {
            ContractApproval approval = contractApprovalRepository.findById(approvalId)
                    .orElseThrow(() -> new RuntimeException("Contract approval not found"));

            String ph = approval.getSignaturePlaceholder();
            if (ph == null || ph.isBlank()) {
                throw new RuntimeException("No signature placeholder defined for this step");
            }

            // Lấy current (PDF hoặc DOCX) để suy ra contractId (hoặc dùng trực tiếp id)
            File file = getContractFile(contractId);
            return embedSignature(file.getAbsolutePath(), imageUrl, ph);
        } catch (Exception e) {
            throw new RuntimeException("Embed signature for approval (OnlyOffice) failed: " + e.getMessage(), e);
        }
    }

    /**
     * Ghi thêm text phê duyệt vào DOCX (cuối tài liệu) rồi convert lại PDF
     */
    @Override
    public void addApprovalText(String filePath, String approvalText) {
        try {
            Long contractId = extractContractIdFromPath(Paths.get(filePath));
            Contract contract = contractRepository.findById(contractId)
                    .orElseThrow(() -> new RuntimeException("Contract not found"));

            Path docx = ensureDocxGenerated(contract, null);

            WordprocessingMLPackage pkg = WordprocessingMLPackage.load(docx.toFile());
            addParagraphWithText(pkg, approvalText, 10, false);
            pkg.save(docx.toFile());

            Path pdf = pdfPathOf(contractId);
            convertToPdfUsingOnlyOffice(contractId, docx, pdf);

            contract.setFilePath(pdf.toString());
            contract.setFileGeneratedAt(LocalDateTime.now());
            contractRepository.save(contract);
        } catch (Exception e) {
            throw new RuntimeException("Add approval text (OnlyOffice) failed: " + e.getMessage(), e);
        }
    }

    /**
     * Lấy danh sách placeholder chữ ký từ các bước approval của contract
     */
    @Override
    public List<String> getSignaturePlaceholders(Long contractId) {
        List<ContractApproval> approvals = contractApprovalRepository.findByContractId(contractId);
        List<String> placeholders = new ArrayList<>();
        for (ContractApproval a : approvals) {
            if (a.getSignaturePlaceholder() != null && !a.getSignaturePlaceholder().isBlank()) {
                placeholders.add(a.getSignaturePlaceholder());
            }
        }
        return placeholders;
    }

    /**
     * Kiểm tra placeholder có tồn tại trong DOCX hiện tại không
     */
    @Override
    public boolean validatePlaceholdersInContract(Long contractId) {
        try {
            Path docx = docxPathOf(contractId);
            if (!Files.exists(docx)) {
                // cố sinh DOCX nếu chưa có
                Contract c = contractRepository.findById(contractId)
                        .orElseThrow(() -> new RuntimeException("Contract not found"));
                generateDocxFile(c);
            }

            List<String> placeholders = getSignaturePlaceholders(contractId);
            if (placeholders.isEmpty()) return false;

            String allText = readAllTextFromDocx(docx);
            for (String ph : placeholders) {
                String normalized = normalizePlaceholder(ph);
                if (!allText.contains(normalized)) {
                    log.warn("Placeholder '{}' not found in DOCX of contract {}", normalized, contractId);
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            log.error("Error validating placeholders for contract {}: {}", contractId, e.getMessage());
            return false;
        }
    }

    /**
     * Sinh DOCX từ template + biến hợp đồng (ở contract.getVariableValues)
     */
    @Override
    public String generateDocxFile(Contract contract) {
        try {
            Path out = docxPathOf(contract.getId());
            Files.createDirectories(out.getParent());

            // chỉ hỗ trợ template DOCX
            if (contract.getTemplate() == null || contract.getTemplate().getFilePath() == null) {
                throw new RuntimeException("Template not found or filePath is null");
            }
            Path templatePath = Paths.get(contract.getTemplate().getFilePath());
            String name = templatePath.getFileName().toString().toLowerCase(Locale.ROOT);
            if (!name.endsWith(".docx")) {
                throw new RuntimeException("Only DOCX template is supported");
            }
            if (!Files.exists(templatePath)) {
                throw new RuntimeException("Template file not found: " + templatePath);
            }

            Map<String, String> map = contract.getVariableValues() == null
                    ? Collections.emptyMap()
                    : contract.getVariableValues().stream()
                    .collect(Collectors.toMap(
                            ContractVariableValue::getVarName,
                            v -> Optional.ofNullable(v.getVarValue()).orElse("")
                    ));

            // biến -> variableReplace
            WordprocessingMLPackage pkg = WordprocessingMLPackage.load(templatePath.toFile());
            VariablePrepare.prepare(pkg);
            pkg.getMainDocumentPart().variableReplace(map);
            pkg.save(out.toFile());

            return out.toString();
        } catch (Exception e) {
            throw new RuntimeException("Generate DOCX failed: " + e.getMessage(), e);
        }
    }

    /**
     * Lấy PDF; nếu chưa có thì convert từ DOCX (tự sinh DOCX nếu cần)
     */
    @Override
    public File getPdfOrConvert(Long contractId) {
        try {
            Path pdf = pdfPathOf(contractId);
            if (Files.exists(pdf)) return pdf.toFile();

            Path docx = docxPathOf(contractId);
            if (!Files.exists(docx)) {
                Contract c = contractRepository.findById(contractId)
                        .orElseThrow(() -> new RuntimeException("Contract not found"));
                generateDocxFile(c);
            }
            convertToPdfUsingOnlyOffice(contractId, docx, pdf);

            // cập nhật DB
            Contract c = contractRepository.findById(contractId).orElseThrow();
            c.setFilePath(pdf.toString());
            c.setFileGeneratedAt(LocalDateTime.now());
            contractRepository.save(c);

            return pdf.toFile();
        } catch (Exception e) {
            throw new RuntimeException("Get PDF or convert failed: " + e.getMessage(), e);
        }
    }

    // ========================================================================
    // Helpers (DOCX, OnlyOffice, Paths, I/O)
    // ========================================================================

    /** Đảm bảo có DOCX cho hợp đồng (sinh nếu chưa) – có thể override biến bằng tham số */
    private Path ensureDocxGenerated(Contract contract, List<ContractVariableValue> overrideVars) {
        try {
            Path docx = docxPathOf(contract.getId());
            Files.createDirectories(docx.getParent());

            if (Files.exists(docx) && Files.size(docx) > 0) {
                return docx;
            }

            // Chỉ hỗ trợ template DOCX
            if (contract.getTemplate() == null || contract.getTemplate().getFilePath() == null) {
                throw new RuntimeException("Template not found or filePath is null");
            }
            Path templatePath = Paths.get(contract.getTemplate().getFilePath());
            if (!Files.exists(templatePath)) {
                throw new RuntimeException("Template file not found: " + templatePath);
            }
            if (!templatePath.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".docx")) {
                throw new RuntimeException("Only DOCX template is supported");
            }

            // Build map biến (ưu tiên override)
            List<ContractVariableValue> src = (overrideVars != null) ? overrideVars : contract.getVariableValues();
            Map<String, String> map = (src == null)
                    ? Collections.emptyMap()
                    : src.stream().collect(Collectors.toMap(
                    ContractVariableValue::getVarName,
                    v -> Optional.ofNullable(v.getVarValue()).orElse("")
            ));

            WordprocessingMLPackage pkg = WordprocessingMLPackage.load(templatePath.toFile());
            VariablePrepare.prepare(pkg);
            pkg.getMainDocumentPart().variableReplace(map);
            pkg.save(docx.toFile());

            return docx;
        } catch (Exception e) {
            throw new RuntimeException("Ensure DOCX generated failed: " + e.getMessage(), e);
        }
    }

    /** Convert DOCX → PDF bằng OnlyOffice ConvertService */
    private void convertToPdfUsingOnlyOffice(Long contractId, Path inputDocx, Path outputPdf) {
        try {
            Files.createDirectories(outputPdf.getParent());

            // OnlyOffice cần URL public để fetch DOCX từ BE
            // Bạn cần một controller nội bộ như:
            // GET /internal/files/{contractId}/contract.docx -> trả FileSystemResource(docxPath)
            final String sourceUrl = hostBaseUrl.replaceAll("/+$", "")
                    + "/internal/files/" + contractId + "/" + DOCX_NAME;

            Map<String, Object> payload = new HashMap<>();
            payload.put("async", false);
            payload.put("filetype", "docx");
            payload.put("outputtype", "pdf");
            payload.put("key", contractId + "-" + System.currentTimeMillis());
            payload.put("title", DOCX_NAME);
            payload.put("url", sourceUrl);

            String json = om.writeValueAsString(payload);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            String base = docServer.replaceAll("/+$", "");
            String[] endpoints = { base + "/ConvertService.ashx", base + "/ConvertService" };

            ResponseEntity<String> resp = null;
            Exception lastEx = null;
            for (String ep : endpoints) {
                try {
                    resp = rest.postForEntity(ep, new HttpEntity<>(json, headers), String.class);
                    if (resp.getStatusCode().is2xxSuccessful()) break;
                } catch (Exception e) {
                    lastEx = e;
                }
            }
            if (resp == null) {
                throw new RuntimeException("ConvertService request failed (no response)", lastEx);
            }
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                throw new RuntimeException("ConvertService HTTP error: " + resp.getStatusCode()
                        + " body=" + (resp.getBody() == null ? "<null>" : resp.getBody()));
            }

            MediaType ct = resp.getHeaders().getContentType();
            if (ct == null || !ct.toString().toLowerCase(Locale.ROOT).contains("application/json")) {
                throw new RuntimeException("ConvertService response is not JSON. contentType=" + ct
                        + ", body=" + resp.getBody());
            }

            JsonNode root = om.readTree(resp.getBody());
            int attempts = 0;
            while (!root.path("endConvert").asBoolean(false) && attempts < 20) {
                Thread.sleep(500);
                resp = rest.postForEntity(endpoints[0], new HttpEntity<>(json, headers), String.class);
                if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                    throw new RuntimeException("ConvertService polling failed: " + resp.getStatusCode());
                }
                root = om.readTree(resp.getBody());
                attempts++;
            }

            if (!root.path("endConvert").asBoolean(false)) {
                int percent = root.path("percent").asInt(-1);
                throw new RuntimeException("ConvertService not finished. percent=" + percent);
            }

            String fileUrl = root.path("fileUrl").asText(null);
            if (fileUrl == null || fileUrl.isBlank()) {
                throw new RuntimeException("ConvertService returned no fileUrl");
            }

            byte[] pdfBytes = rest.getForObject(fileUrl, byte[].class);
            if (pdfBytes == null || pdfBytes.length == 0) {
                throw new RuntimeException("Downloaded PDF is empty from: " + fileUrl);
            }

            Files.write(outputPdf, pdfBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        } catch (Exception e) {
            throw new RuntimeException("Convert via ONLYOFFICE failed: " + e.getMessage(), e);
        }
    }

    // ========================================================================
    // DOCX helpers (placeholder, image/text insert, read text)
    // ========================================================================

    private boolean replacePlaceholderWithImage(WordprocessingMLPackage pkg,
                                                String placeholder,
                                                byte[] imageBytes,
                                                Float widthPx, Float heightPx) throws Exception {
        long cx = pxToEmu(widthPx != null ? widthPx : DEFAULT_SIG_W);
        long cy = pxToEmu(heightPx != null ? heightPx : DEFAULT_SIG_H);

        var mdp = pkg.getMainDocumentPart();
        List<Object> content = mdp.getContent();

        BinaryPartAbstractImage imagePart = BinaryPartAbstractImage.createImagePart(pkg, imageBytes);
        var inline = imagePart.createImageInline("signature", "signature", 0, 1, cx, cy, false);

        for (int pi = 0; pi < content.size(); pi++) {
            Object pObj = unwrap(content.get(pi));
            if (pObj instanceof P p) {
                List<Object> runs = p.getContent();
                for (int ri = 0; ri < runs.size(); ri++) {
                    Object rObj = unwrap(runs.get(ri));
                    if (rObj instanceof R r) {
                        for (int ti = 0; ti < r.getContent().size(); ti++) {
                            Object tObj = unwrap(r.getContent().get(ti));
                            if (tObj instanceof Text t) {
                                String val = t.getValue();
                                if (val != null && val.contains(placeholder)) {
                                    int idx = val.indexOf(placeholder);
                                    String before = val.substring(0, idx);
                                    String after  = val.substring(idx + placeholder.length());

                                    t.setValue(before);

                                    Drawing drawing = WML.createDrawing();
                                    drawing.getAnchorOrInline().add(inline);
                                    R imgRun = WML.createR();
                                    imgRun.getContent().add(drawing);
                                    runs.add(ri + 1, imgRun);

                                    if (!after.isEmpty()) {
                                        R afterRun = WML.createR();
                                        Text afterText = WML.createText();
                                        afterText.setValue(after);
                                        afterRun.getContent().add(afterText);
                                        runs.add(ri + 2, afterRun);
                                    }
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private void addParagraphWithText(WordprocessingMLPackage pkg, String text, int fontSizePt, boolean bold) {
        P p = WML.createP();
        R r = WML.createR();
        Text t = WML.createText();
        t.setValue(text);

        RPr rPr = WML.createRPr();
        if (bold) {
            BooleanDefaultTrue b = new BooleanDefaultTrue();
            b.setVal(true);
            rPr.setB(b);
        }
        HpsMeasure size = new HpsMeasure();
        size.setVal(BigInteger.valueOf(Math.max(2, fontSizePt) * 2L)); // half-points
        rPr.setSz(size);
        rPr.setSzCs(size);

        r.setRPr(rPr);
        r.getContent().add(t);

        p.getContent().add(r);
        pkg.getMainDocumentPart().addObject(p);
    }

    private String readAllTextFromDocx(Path docx) {
        try {
            WordprocessingMLPackage pkg = WordprocessingMLPackage.load(docx.toFile());
            StringBuilder sb = new StringBuilder();
            collectText(pkg.getMainDocumentPart().getContent(), sb);
            return sb.toString();
        } catch (Exception e) {
            log.warn("readAllTextFromDocx error: {}", e.getMessage());
            return "";
        }
    }

    private void collectText(List<Object> content, StringBuilder sb) {
        for (Object o : content) {
            Object u = unwrap(o);
            if (u instanceof P p) {
                for (Object rObj : p.getContent()) {
                    Object ur = unwrap(rObj);
                    if (ur instanceof R r) {
                        for (Object tObj : r.getContent()) {
                            Object ut = unwrap(tObj);
                            if (ut instanceof Text t) {
                                sb.append(t.getValue());
                            }
                        }
                    }
                }
                sb.append('\n');
            }
        }
    }

    private String normalizePlaceholder(String raw) {
        String s = raw.trim();
        // Cho phép user truyền "KEY" hoặc "{{SIGN:KEY}}" hoặc "{{KEY}}"
        if (s.startsWith("{{") && s.endsWith("}}")) return s;
        if (s.startsWith("SIGN:") || s.startsWith("sign:")) return "{{" + s + "}}";
        return "{{SIGN:" + s + "}}";
    }

    private long pxToEmu(float px) { return Math.round(px * 9525f); }

    private Object unwrap(Object o) { return (o instanceof JAXBElement<?> je) ? je.getValue() : o; }

    // ========================================================================
    // I/O helpers
    // ========================================================================

    /** Hỗ trợ data: URL, http(s), file path (tuyệt đối/ tương đối), và đường dẫn trong signatureStorageDir */
    private byte[] loadImageBytes(String imageUrlOrPath) {
        if (imageUrlOrPath == null || imageUrlOrPath.isBlank()) return null;
        try {
            // data:image/png;base64,...
            if (imageUrlOrPath.startsWith("data:")) {
                int comma = imageUrlOrPath.indexOf(',');
                if (comma > 0) {
                    String b64 = imageUrlOrPath.substring(comma + 1);
                    return Base64.getDecoder().decode(b64);
                }
                return null;
            }

            // http(s)
            if (imageUrlOrPath.startsWith("http://") || imageUrlOrPath.startsWith("https://")) {
                try (InputStream in = new java.net.URL(imageUrlOrPath).openStream();
                     ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                    in.transferTo(bos);
                    return bos.toByteArray();
                }
            }

            // File system (absolute/relative)
            Path p = Paths.get(imageUrlOrPath);
            if (Files.exists(p)) return Files.readAllBytes(p);

            // Thử relative với signatureStorageDir
            Path rel = Paths.get(signatureStorageDir, imageUrlOrPath).normalize();
            if (Files.exists(rel)) return Files.readAllBytes(rel);

            log.error("Signature image not found at: {} or {}", p, rel);
            return null;
        } catch (Exception e) {
            log.error("loadImageBytes fail: {}", e.getMessage(), e);
            return null;
        }
    }

    private Long extractContractIdFromPath(Path anyPathUnderContractFolder) {
        // Expect: uploads/contracts/{id}/...
        Path parent = anyPathUnderContractFolder.getParent();
        while (parent != null && !parent.getFileName().toString().equals("contracts")) {
            try {
                long id = Long.parseLong(parent.getFileName().toString());
                return id;
            } catch (NumberFormatException ignore) {
            }
            parent = parent.getParent();
        }
        throw new IllegalArgumentException("Cannot extract contractId from path: " + anyPathUnderContractFolder);
    }

    private Path contractDirOf(Long id) {
        return Paths.get(System.getProperty("user.dir"), UPLOAD_ROOT, String.valueOf(id)).normalize();
    }

    private Path docxPathOf(Long id) {
        return contractDirOf(id).resolve(DOCX_NAME);
    }

    private Path pdfPathOf(Long id) {
        return contractDirOf(id).resolve(PDF_NAME);
    }
}
