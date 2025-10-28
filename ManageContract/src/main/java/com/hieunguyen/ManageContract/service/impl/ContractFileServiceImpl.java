package com.hieunguyen.ManageContract.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hieunguyen.ManageContract.entity.Contract;
import com.hieunguyen.ManageContract.entity.ContractApproval;
import com.hieunguyen.ManageContract.entity.ContractVariableValue;
import com.hieunguyen.ManageContract.repository.ContractApprovalRepository;
import com.hieunguyen.ManageContract.repository.ContractRepository;
import com.hieunguyen.ManageContract.service.ContractFileService;
import jakarta.transaction.Transactional;
import jakarta.xml.bind.JAXBElement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.docx4j.model.datastorage.migration.VariablePrepare;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.BinaryPartAbstractImage;
import org.docx4j.wml.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.text.Normalizer;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ContractFileServiceImpl implements ContractFileService {

    private static final ObjectFactory WML = new ObjectFactory();
    private static final String UPLOAD_ROOT = "uploads/contracts";
    private static final String DOCX_NAME   = "contract.docx";
    private static final String PDF_NAME    = "contract.pdf";

    // kích thước chữ ký mặc định (px, giả định ảnh 96 DPI)
    private static final float DEFAULT_SIG_W = 180f;
    private static final float DEFAULT_SIG_H = 60f;

    private final ContractRepository contractRepository;
    private final ContractApprovalRepository contractApprovalRepository;

    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper om = new ObjectMapper();

    @Value("${app.ds.url:http://documentserver}")
    private String docServer; // ONLYOFFICE Document Server

    @Value("${app.ds.source-base:http://app:8080}")
    private String hostBaseUrl; // BE base URL cho DS fetch file

    @Value("${app.signature.storage-dir:uploads/signatures}")
    private String signatureStorageDir;

    // ========================================================================
    // ContractFileService API
    // ========================================================================

    /** Sinh DOCX từ template + variables, rồi convert PDF bằng OnlyOffice (1 lần) */
    @Override
    public String generateContractFile(Contract contract) {
        try {
            Path docx = ensureDocxGenerated(contract, null);
            Path pdf  = pdfPathOf(contract.getId());

            // không thêm nhật ký ở đây; chỉ cập nhật khi có hành động ký/phê duyệt
            convertToPdfUsingOnlyOffice(contract.getId(), docx, pdf);

            contract.setFilePath(pdf.toString());
            contract.setFileGeneratedAt(LocalDateTime.now());
            contractRepository.save(contract);

            return pdf.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate contract file: " + e.getMessage(), e);
        }
    }

    /** Sinh file với biến truyền vào (ưu tiên tham số) */
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

    /** Lấy file gốc: ưu tiên PDF, sau đó DOCX, cuối cùng DB filePath nếu tồn tại */
    @Override
    public File getContractFile(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        Path pdf  = pdfPathOf(contractId);
        Path docx = docxPathOf(contractId);

        if (Files.exists(pdf)) return pdf.toFile();
        if (Files.exists(docx)) return docx.toFile();

        if (contract.getFilePath() != null && Files.exists(Paths.get(contract.getFilePath()))) {
            return Paths.get(contract.getFilePath()).toFile();
        }
        throw new RuntimeException("Contract file not generated yet");
    }

    /**
     * Ký theo placeholder trên DOCX rồi convert PDF.
     * Trang "Nhật ký ký duyệt" cuối văn bản sẽ được REPLACE (chỉ 1 trang).
     */
    @Override
    @Transactional
    public String embedSignature(String filePath, String imageUrl, String placeholder) {
        try {
            if (placeholder == null || placeholder.isBlank()) {
                throw new RuntimeException("Placeholder is required");
            }

            Long contractId = extractContractIdFromPath(Paths.get(filePath));
            Contract contract = contractRepository.findById(contractId)
                    .orElseThrow(() -> new RuntimeException("Contract not found"));
            Path docx = ensureDocxGenerated(contract, null);

            byte[] imageBytes = loadImageBytes(imageUrl);
            if (imageBytes == null || imageBytes.length == 0) {
                throw new RuntimeException("Signature image is empty or invalid");
            }

            WordprocessingMLPackage pkg = WordprocessingMLPackage.load(docx.toFile());
            boolean ok = replacePlaceholderWithImage(
                    pkg,
                    normalizePlaceholder(placeholder),
                    imageBytes,
                    DEFAULT_SIG_W,
                    DEFAULT_SIG_H
            );
            if (!ok) throw new RuntimeException("Placeholder not found in DOCX: " + placeholder);

            // === Cập nhật trang "Nhật ký ký duyệt" (chỉ 1 trang) ===
            updateApprovalLogPage(contractId, pkg);

            pkg.save(docx.toFile());

            Path pdf = pdfPathOf(contractId);
            convertToPdfUsingOnlyOffice(contractId, docx, pdf);

            contract.setFilePath(pdf.toString());
            contract.setFileGeneratedAt(LocalDateTime.now());
            contractRepository.save(contract);

            return pdf.toString();
        } catch (Exception e) {
            throw new RuntimeException("Embed signature (OnlyOffice) failed: " + e.getMessage(), e);
        }
    }

    /** Ký theo step phê duyệt: dùng placeholder của step hoặc mặc định SIGN:<approvalId> */
    @Override
    @Transactional
    public String embedSignatureForApproval(Long contractId, String imageUrl, Long approvalId) {
        try {
            ContractApproval approval = contractApprovalRepository.findById(approvalId)
                    .orElseThrow(() -> new RuntimeException("Contract approval not found"));

            String ph = Optional.ofNullable(approval.getSignaturePlaceholder())
                    .filter(s -> !s.isBlank())
                    .orElse("SIGN:" + approvalId);

            File file = getContractFile(contractId);
            return embedSignature(file.getAbsolutePath(), imageUrl, ph);
        } catch (Exception e) {
            throw new RuntimeException("Embed signature for approval (OnlyOffice) failed: " + e.getMessage(), e);
        }
    }

    /** Ghi text phê duyệt vào DOCX rồi convert PDF (và replace nhật ký) */
    @Override
    @Transactional
    public void addApprovalText(String filePath, String approvalText) {
        try {
            Long contractId = extractContractIdFromPath(Paths.get(filePath));
            Contract contract = contractRepository.findById(contractId)
                    .orElseThrow(() -> new RuntimeException("Contract not found"));

            Path docx = ensureDocxGenerated(contract, null);

            WordprocessingMLPackage pkg = WordprocessingMLPackage.load(docx.toFile());
            addParagraphWithText(pkg, approvalText, 10, false);

            // === Cập nhật trang "Nhật ký ký duyệt" (chỉ 1 trang) ===
            updateApprovalLogPage(contractId, pkg);

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

    /** Liệt kê placeholder từ các bước */
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

    /** Kiểm tra placeholder có tồn tại trong DOCX hiện tại không */
    @Override
    public boolean validatePlaceholdersInContract(Long contractId) {
        try {
            Path docx = docxPathOf(contractId);
            if (!Files.exists(docx)) {
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

    /** Sinh DOCX từ template + biến contract */
    @Override
    public String generateDocxFile(Contract contract) {
        try {
            Path out = docxPathOf(contract.getId());
            Files.createDirectories(out.getParent());

            if (contract.getTemplate() == null || contract.getTemplate().getFilePath() == null) {
                throw new RuntimeException("Template not found or filePath is null");
            }
            Path templatePath = Paths.get(contract.getTemplate().getFilePath());
            String name = templatePath.getFileName().toString().toLowerCase(Locale.ROOT);
            if (!name.endsWith(".docx")) throw new RuntimeException("Only DOCX template is supported");
            if (!Files.exists(templatePath)) throw new RuntimeException("Template file not found: " + templatePath);

            Map<String, String> map = contract.getVariableValues() == null
                    ? Collections.emptyMap()
                    : contract.getVariableValues().stream()
                    .collect(Collectors.toMap(
                            ContractVariableValue::getVarName,
                            v -> Optional.ofNullable(v.getVarValue()).orElse("")
                    ));

            WordprocessingMLPackage pkg = WordprocessingMLPackage.load(templatePath.toFile());
            VariablePrepare.prepare(pkg);
            pkg.getMainDocumentPart().variableReplace(map);
            pkg.save(out.toFile());

            return out.toString();
        } catch (Exception e) {
            throw new RuntimeException("Generate DOCX failed: " + e.getMessage(), e);
        }
    }

    /** Lấy PDF; nếu chưa có thì convert từ DOCX (tự sinh DOCX nếu cần) */
    @Override
    @Transactional
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

            WordprocessingMLPackage pkg = WordprocessingMLPackage.load(docx.toFile());
            updateApprovalLogPage(contractId, pkg);
            pkg.save(docx.toFile());

            convertToPdfUsingOnlyOffice(contractId, docx, pdf);

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
    // (Tuỳ chọn) KÝ THEO TÊN – chèn ảnh trên tên (nếu bạn dùng luồng này)
    // ========================================================================

    @Override
    public String embedSignatureByName(Long contractId, String imageRef, String printedName) {
        return embedSignatureByNameOnDocx(contractId, imageRef, printedName, null, null);
    }

    public String embedSignatureByNameOnDocx(Long contractId, String imageRef, String printedName,
                                             Float widthPx, Float heightPx) {
        try {
            if (printedName == null || printedName.isBlank()) {
                throw new RuntimeException("printedName is required");
            }

            Contract contract = contractRepository.findById(contractId)
                    .orElseThrow(() -> new RuntimeException("Contract not found"));
            Path docx = ensureDocxGenerated(contract, null);

            byte[] imageBytes = loadImageBytes(imageRef);
            if (imageBytes == null || imageBytes.length == 0) {
                throw new RuntimeException("Signature image is empty or invalid");
            }

            WordprocessingMLPackage pkg = WordprocessingMLPackage.load(docx.toFile());
            boolean ok = insertImageAboveName(pkg, printedName, imageBytes,
                    (widthPx  != null ? widthPx  : DEFAULT_SIG_W),
                    (heightPx != null ? heightPx : DEFAULT_SIG_H));
            if (!ok) throw new RuntimeException("Không tìm thấy tên trong DOCX: " + printedName);

            // === Replace trang nhật ký ===
            updateApprovalLogPage(contractId, pkg);

            pkg.save(docx.toFile());

            Path pdfPath = pdfPathOf(contractId);
            convertToPdfUsingOnlyOffice(contractId, docx, pdfPath);

            contract.setFilePath(pdfPath.toString());
            contract.setFileGeneratedAt(LocalDateTime.now());
            contractRepository.save(contract);

            return pdfPath.toString();

        } catch (Exception e) {
            throw new RuntimeException("Embed signature by NAME (DOCX-first) failed: " + e.getMessage(), e);
        }
    }

    // ========================================================================
    // Helpers (DOCX, OnlyOffice, Paths, I/O)
    // ========================================================================

    /** Đảm bảo có DOCX cho hợp đồng; có thể override biến */
    private Path ensureDocxGenerated(Contract contract, List<ContractVariableValue> overrideVars) {
        try {
            Path docx = docxPathOf(contract.getId());
            Files.createDirectories(docx.getParent());

            if (Files.exists(docx) && Files.size(docx) > 0) return docx;

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

            List<ContractVariableValue> src = (overrideVars != null) ? overrideVars : contract.getVariableValues();
            Map<String, String> map = (src == null) ? Collections.emptyMap()
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
                                String val = Optional.ofNullable(t.getValue()).orElse("");

                                // Ưu tiên: placeholder đứng riêng một run
                                if (val.trim().equals(placeholder)) {
                                    t.setValue("");

                                    Drawing drawing = WML.createDrawing();
                                    drawing.getAnchorOrInline().add(inline);
                                    R imgRun = WML.createR();
                                    imgRun.getContent().add(drawing);
                                    runs.add(ri + 1, imgRun);
                                    return true;
                                }

                                // Fallback: placeholder nằm trọn trong cùng Text (không cắt qua run khác)
                                int idx = val.indexOf(placeholder);
                                if (idx >= 0) {
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
        if (raw == null) return "{{SIGN}}";
        String s = raw.trim();

        if (s.startsWith("{{") && s.endsWith("}}")) return s;

        if (s.startsWith("[") && s.endsWith("]")) {
            s = s.substring(1, s.length() - 1).trim();
        }

        if (s.equalsIgnoreCase("SIGN")) return "{{SIGN}}";

        if (s.regionMatches(true, 0, "SIGN:", 0, 5)) return "{{" + s + "}}";

        return "{{SIGN:" + s + "}}";
    }

    private long pxToEmu(float px) { return Math.round(px * 9525f); }

    private Object unwrap(Object o) { return (o instanceof JAXBElement<?> je) ? je.getValue() : o; }

    // ========================================================================
    // DOCX name matching & insert image after/above a full name
    // ========================================================================

    private static String foldAccents(String s) {
        if (s == null) return null;
        String n = Normalizer.normalize(s, Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}+", "");
    }
    private static String collapseSpaces(String s) {
        return s == null ? null : s.replace('\u00A0',' ').trim().replaceAll("\\s+"," ");
    }

    private boolean insertImageAfterName(WordprocessingMLPackage pkg, String name,
                                         byte[] imageBytes, float wPx, float hPx) throws Exception {
        long cx = pxToEmu(wPx), cy = pxToEmu(hPx);
        var imgPart = BinaryPartAbstractImage.createImagePart(pkg, imageBytes);
        var inline  = imgPart.createImageInline("signature","signature",0,1,cx,cy,false);

        List<Object> blocks = pkg.getMainDocumentPart().getContent();
        P lastPara = null; R endRun = null;

        for (Object o : blocks) {
            Object u = unwrap(o);
            if (!(u instanceof P p)) continue;

            @SuppressWarnings("unchecked")
            List<Object> runs = p.getContent();

            StringBuilder joined = new StringBuilder();
            List<R> idxToRun = new ArrayList<>();
            for (Object rObj : runs) {
                Object ur = unwrap(rObj);
                if (!(ur instanceof R r)) continue;
                for (Object tObj : r.getContent()) {
                    Object ut = unwrap(tObj);
                    if (ut instanceof Text t) {
                        String val = Optional.ofNullable(t.getValue()).orElse("");
                        if (!val.isEmpty()) {
                            String norm = val.replace('\u00A0',' ');
                            joined.append(norm);
                            for (int i=0;i<norm.length();i++) idxToRun.add(r);
                        }
                    }
                }
                joined.append(' '); idxToRun.add((R) (unwrap(rObj)));
            }

            String hayExact = collapseSpaces(joined.toString().toLowerCase(Locale.ROOT));
            String hayFold  = collapseSpaces(foldAccents(joined.toString()).toLowerCase(Locale.ROOT));
            String needleE  = collapseSpaces(name.toLowerCase(Locale.ROOT));
            String needleF  = collapseSpaces(foldAccents(name).toLowerCase(Locale.ROOT));

            int end = lastIndexEnd(hayExact, needleE);
            if (end < 0) end = lastIndexEnd(hayFold, needleF);
            if (end < 0 || idxToRun.isEmpty()) continue;

            endRun  = idxToRun.get(Math.min(end, idxToRun.size()-1));
            lastPara = p;
        }

        if (lastPara == null || endRun == null) return false;

        Drawing drawing = WML.createDrawing();
        drawing.getAnchorOrInline().add(inline);
        R imgRun = WML.createR(); imgRun.getContent().add(drawing);

        List<Object> runs = lastPara.getContent();
        for (int i=0;i<runs.size();i++) {
            if (unwrap(runs.get(i)) == endRun) {
                runs.add(i+1, imgRun);
                return true;
            }
        }
        runs.add(imgRun);
        return true;
    }

    private int lastIndexEnd(String hay, String needle){
        if (hay==null||needle==null||needle.isBlank()) return -1;
        int from=0, end=-1;
        while (true) {
            int idx = hay.indexOf(needle, from);
            if (idx<0) break;
            end = idx + needle.length() - 1;
            from = idx + 1;
        }
        return end;
    }

    private boolean insertImageAboveName(WordprocessingMLPackage pkg, String name,
                                         byte[] imageBytes, float wPx, float hPx) throws Exception {
        long cx = pxToEmu(wPx), cy = pxToEmu(hPx);
        var imgPart = BinaryPartAbstractImage.createImagePart(pkg, imageBytes);
        var inline  = imgPart.createImageInline("signature","signature",0,1,cx,cy,false);

        List<Object> blocks = pkg.getMainDocumentPart().getContent();
        P targetPara = null; R startRun = null;

        for (Object o : blocks) {
            Object u = unwrap(o);
            if (!(u instanceof P p)) continue;

            @SuppressWarnings("unchecked")
            List<Object> runs = p.getContent();

            StringBuilder joined = new StringBuilder();
            List<R> idxToRun = new ArrayList<>();
            for (Object rObj : runs) {
                Object ur = unwrap(rObj);
                if (!(ur instanceof R r)) continue;
                for (Object tObj : r.getContent()) {
                    Object ut = unwrap(tObj);
                    if (ut instanceof Text t) {
                        String val = Optional.ofNullable(t.getValue()).orElse("");
                        if (!val.isEmpty()) {
                            String norm = val.replace('\u00A0',' ');
                            joined.append(norm);
                            for (int i=0;i<norm.length();i++) idxToRun.add(r);
                        }
                    }
                }
                joined.append(' ');
                idxToRun.add((R) unwrap(rObj));
            }

            String hayExact = collapseSpaces(joined.toString().toLowerCase(Locale.ROOT));
            String hayFold  = collapseSpaces(foldAccents(joined.toString()).toLowerCase(Locale.ROOT));
            String needleE  = collapseSpaces(name.toLowerCase(Locale.ROOT));
            String needleF  = collapseSpaces(foldAccents(name).toLowerCase(Locale.ROOT));

            int end = lastIndexEnd(hayExact, needleE);
            int nlen = (needleE != null ? needleE.length() : 0);
            if (end < 0) { end = lastIndexEnd(hayFold, needleF); nlen = (needleF != null ? needleF.length() : 0); }
            if (end < 0 || idxToRun.isEmpty()) continue;

            int start = Math.max(0, end - nlen + 1);
            startRun  = idxToRun.get(Math.min(start, idxToRun.size()-1));
            targetPara = p;
        }

        if (targetPara == null || startRun == null) return false;

        Drawing drawing = WML.createDrawing();
        drawing.getAnchorOrInline().add(inline);
        Br br = WML.createBr(); // xuống dòng sau ảnh

        List<Object> rc = startRun.getContent();
        rc.add(0, br);       // tên nằm ngay dưới ảnh
        rc.add(0, drawing);  // ảnh trùng vị trí ký tự đầu

        return true;
    }

    // ========================================================================
    // APPROVAL LOG (1 trang duy nhất, replace mỗi lần)
    // ========================================================================

    /** Cập nhật (replace) đúng 1 trang "NHẬT KÝ KÝ DUYỆT" ở cuối DOCX */
    private void updateApprovalLogPage(Long contractId, WordprocessingMLPackage pkg) {
        try {
            // Xoá block cũ + page-break cũ (nếu còn)
            removeApprovalLogBlockWithLeadingPageBreak(pkg);

            // Lấy dữ liệu approvals
            List<ContractApproval> approvals = contractApprovalRepository.findAllForLog(contractId);
            if (approvals == null || approvals.isEmpty()) return;

            approvals.sort(
                    Comparator
                            .comparing((ContractApproval a) ->
                                    Optional.ofNullable(a.getStepOrder()).orElse(Integer.MAX_VALUE))
                            .thenComparing(a ->
                                    Optional.ofNullable(a.getApprovedAt())
                                            .orElse(Optional.ofNullable(a.getUpdatedAt()).orElse(LocalDateTime.MIN)))
            );

            // Dựng 1 SdtBlock (APPROVAL_LOG) – page break là PHẦN TỬ ĐẦU TIÊN bên trong block
            SdtBlock sdt = WML.createSdtBlock();
            SdtPr pr = WML.createSdtPr();
            Tag tag = WML.createTag(); tag.setVal("APPROVAL_LOG");
            pr.setTag(tag); sdt.setSdtPr(pr);
            SdtContentBlock content = WML.createSdtContentBlock();
            sdt.setSdtContent(content);

            // Ngắt sang trang mới (bên trong block → replace sẽ xoá luôn)
            content.getContent().add(pageBreakParagraph());

            // Tiêu đề
            content.getContent().add(titleParagraph("NHẬT KÝ KÝ DUYỆT VĂN BẢN", 14));
            content.getContent().add(emptyLine());

            // Bảng dữ liệu
            Tbl tbl = buildApprovalTable(approvals);
            content.getContent().add(tbl);

            // Gắn block vào cuối tài liệu
            pkg.getMainDocumentPart().addObject(sdt);
        } catch (Exception e) {
            log.warn("updateApprovalLogPage error: {}", e.getMessage());
        }
    }

    /** Xoá SdtBlock tag=APPROVAL_LOG và cả paragraph page-break liền trước (nếu tồn tại từ bản cũ) */
    private void removeApprovalLogBlockWithLeadingPageBreak(WordprocessingMLPackage pkg) {
        var mdp = pkg.getMainDocumentPart();
        List<Object> content = mdp.getContent();
        for (int i = 0; i < content.size(); i++) {
            Object u = unwrap(content.get(i));
            if (u instanceof SdtBlock s) {
                SdtPr pr = s.getSdtPr();
                if (pr != null && pr.getTag() != null && "APPROVAL_LOG".equals(pr.getTag().getVal())) {
                    // nếu phần tử ngay trước là paragraph chỉ chứa page-break thì xoá luôn
                    if (i > 0 && isPageBreakParagraph(content.get(i - 1))) {
                        content.remove(i - 1); // remove break
                        content.remove(i - 1); // sau khi remove, block trượt về i-1
                    } else {
                        content.remove(i);
                    }
                    break; // chỉ có 1 block
                }
            }
        }
    }

    /** Paragraph chỉ có page-break? */
    private boolean isPageBreakParagraph(Object node) {
        Object u = unwrap(node);
        if (!(u instanceof P p)) return false;
        for (Object rObj : p.getContent()) {
            Object ur = unwrap(rObj);
            if (ur instanceof R r) {
                for (Object tObj : r.getContent()) {
                    Object ut = unwrap(tObj);
                    if (ut instanceof Br br) {
                        return br.getType() == null || br.getType() == STBrType.PAGE;
                    }
                }
            }
        }
        return false;
    }

    /** Tạo bảng: STT | Người xử lý | Đơn vị | Thời gian | Ý kiến */
    private Tbl buildApprovalTable(List<ContractApproval> approvals) {
        Tbl tbl = WML.createTbl();

        // ===== borders =====
        TblPr pr = WML.createTblPr();
        TblBorders borders = WML.createTblBorders();
        CTBorder b = WML.createCTBorder();
        b.setVal(STBorder.SINGLE);
        b.setSz(BigInteger.valueOf(8));
        b.setColor("BFBFBF");
        borders.setTop(b); borders.setBottom(b); borders.setLeft(b); borders.setRight(b);
        borders.setInsideH(b); borders.setInsideV(b);
        pr.setTblBorders(borders);
        tbl.setTblPr(pr);

        // ===== header =====
        Tr head = WML.createTr();
        head.getContent().add(thCell("STT",        900));
        head.getContent().add(thCell("Người xử lý",3800));
        head.getContent().add(thCell("Đơn vị",     3200));
        head.getContent().add(thCell("Thời gian",  2200));
        head.getContent().add(thCell("Ý kiến",     2800));
        tbl.getContent().add(head);

        // ===== rows =====
        int i = 1;
        for (ContractApproval a : approvals) {
            Tr tr = WML.createTr();

            tr.getContent().add(tdCell(String.valueOf(i++), 900, true));

            // Người xử lý: bắt approvedBy/signer + assigned/createdBy/updatedBy...
            String actor = extractActorName(a);
            tr.getContent().add(tdCell(actor, 3800, false));

            // Đơn vị: department của approval/actor (nếu có)
            String orgUnit = extractOrgUnit(a);
            tr.getContent().add(tdCell(orgUnit, 3200, false));

            // Thời gian: ưu tiên approvedAt -> updatedAt -> createdAt
            LocalDateTime ts = Optional.ofNullable(getApprovedAt(a))
                    .orElse(Optional.ofNullable(getUpdatedAt(a)).orElse(getCreatedAt(a)));
            tr.getContent().add(tdCell(formatTs(ts), 2200, false));

            String note = firstNonBlank(
                    safe(() -> invokeString(a, "getDecisionNote")),
                    safe(() -> invokeString(a, "getComment")),
                    safe(() -> invokeString(a, "getNote")),
                    ""
            );
            tr.getContent().add(tdCell(note, 2800, false));

            tbl.getContent().add(tr);
        }
        return tbl;
    }
    private boolean notBlank(String s){ return s != null && !s.isBlank(); }
    private String extractActorName(ContractApproval a) {
        if (a == null) return "-";

        // Ưu tiên: approver trực tiếp
        try {
            if (a.getApprover() != null) {
                String n = deepName(a.getApprover());
                if (notBlank(n)) return n;
            }
        } catch (Throwable ignore) {}

        // Nếu entity có chuỗi sẵn trên chính Approval
        String fromSelf = firstNonBlank(
                safe(() -> invokeString(a, "getApproverName")),
                safe(() -> invokeString(a, "getSignerName")),
                safe(() -> invokeString(a, "getActorName"))
        );
        if (notBlank(fromSelf)) return fromSelf;

        // Fallback reflection như cũ
        Object o = scanObjectGetters(
                a,
                List.of("approv","sign","actor","user","employee","account",
                        "handler","processor","review","creator","created",
                        "updater","updated","owner","assign","assignee","assigned")
        );
        String deep = deepName(o);
        return notBlank(deep) ? deep : "-";
    }


    private String extractOrgUnit(Object a) {
        if (a == null) return "-";
        // 1) String-getter departmentName/orgName/...
        String v = scanStringGetters(
                a,
                List.of("departmentname","departmentName","deptname","orgname","organizationname","unitname","phongban","phongBan"),
                List.of("department","dept","org","organization","unit","phong","ban")
        );
        if (notBlank(v)) return v;

        // 2) Object-getter department/unit
        Object dep = scanObjectGetters(a, List.of("department","dept","org","organization","unit"));
        String deep = deepName(dep);
        if (notBlank(deep)) return deep;

        // 3) Đi từ actor -> department
        Object actorObj = scanObjectGetters(a, List.of("approv","sign","actor","user","employee","account","assign","assignee","assigned","creator","created","updater","updated"));
        if (actorObj != null) {
            Object actorDep = scanObjectGetters(actorObj, List.of("department","dept","organization","unit"));
            String viaActor = deepName(actorDep);
            if (notBlank(viaActor)) return viaActor;
        }
        return "-";
    }

    private String scanStringGetters(Object root, List<String> valueKeys, List<String> roleKeys) {
        try {
            for (var m : root.getClass().getMethods()) {
                if (!m.getName().startsWith("get") || m.getParameterCount()!=0 || m.getReturnType()!=String.class) continue;
                String lower = m.getName().toLowerCase(Locale.ROOT);
                boolean hitVal  = valueKeys.stream().anyMatch(lower::contains);
                boolean hitRole = roleKeys.stream().anyMatch(lower::contains);
                if (hitVal && hitRole) {
                    String v = (String) m.invoke(root);
                    if (notBlank(v)) return v;
                }
            }
        } catch (Exception ignore) {}
        return null;
    }

    private Object scanObjectGetters(Object root, List<String> roleKeys) {
        try {
            for (var m : root.getClass().getMethods()) {
                if (!m.getName().startsWith("get") || m.getParameterCount()!=0) continue;
                Class<?> rt = m.getReturnType();
                if (rt.isPrimitive() || rt==String.class) continue;
                String lower = m.getName().toLowerCase(Locale.ROOT);
                if (roleKeys.stream().anyMatch(lower::contains)) {
                    Object o = m.invoke(root);
                    if (o != null) return o;
                }
            }
        } catch (Exception ignore) {}
        return null;
    }


    private String deepName(Object o) {
        if (o == null) return null;
        try {
            // Các getter chuỗi phổ biến
            for (String g : List.of(
                    "getFullName","getFullname","getDisplayName","getName",
                    "getUsername","getUserName","getEmail","getEmailAddress"
            )) {
                try {
                    var m = o.getClass().getMethod(g);
                    Object v = m.invoke(o);
                    if (v instanceof String s && notBlank(s)) return s;
                } catch (NoSuchMethodException ignore) {}
            }

            // Ghép họ tên
            String fn = tryStr(o, "getFirstName", "getGivenName");
            String ln = tryStr(o, "getLastName", "getFamilyName", "getSurname");
            if (notBlank(fn) || notBlank(ln)) {
                return (notBlank(fn)?fn:"") + (notBlank(fn)&&notBlank(ln)?" ":"") + (notBlank(ln)?ln:"");
            }

            // Đi sâu qua các wrapper quen thuộc
            for (String g : List.of("getUser","getAccount","getEmployee","getPerson","getOwner")) {
                try {
                    var m = o.getClass().getMethod(g);
                    String s = deepName(m.invoke(o));
                    if (notBlank(s)) return s;
                } catch (NoSuchMethodException ignore) {}
            }

            // Bất kỳ getter chứa "name"
            for (var m : o.getClass().getMethods()) {
                if (!m.getName().startsWith("get") || m.getParameterCount()!=0 || m.getReturnType()!=String.class) continue;
                if (m.getName().toLowerCase(Locale.ROOT).contains("name")) {
                    String v = (String) m.invoke(o);
                    if (notBlank(v)) return v;
                }
            }

            String ts = String.valueOf(o);
            if (notBlank(ts) && !ts.matches(".+@\\p{XDigit}+")) return ts;
        } catch (Exception ignore) {}
        return null;
    }
    private String tryStr(Object o, String... getters) {
        for (String g : getters) {
            try {
                var m = o.getClass().getMethod(g);
                Object v = m.invoke(o);
                if (v instanceof String s && notBlank(s)) return s;
            } catch (Exception ignore) {}
        }
        return null;
    }

    private Tc thCell(String text, int width) {
        Tc tc = tdCell(text, width, true);
        // shading xám nhạt
        TcPr p = tc.getTcPr(); if (p==null){ p=WML.createTcPr(); tc.setTcPr(p);}
        CTShd shd = WML.createCTShd(); shd.setVal(STShd.CLEAR); shd.setFill("EDEDED");
        p.setShd(shd);
        return tc;
    }
    private Tc tdCell(String text, int width, boolean center) {
        Tc tc = WML.createTc();
        TcPr pr = WML.createTcPr();
        TblWidth w = WML.createTblWidth();
        w.setType("dxa"); w.setW(BigInteger.valueOf(width));
        pr.setTcW(w); tc.setTcPr(pr);

        P p = WML.createP();
        if (center) {
            PPr ppr = WML.createPPr(); Jc jc = WML.createJc(); jc.setVal(JcEnumeration.CENTER);
            ppr.setJc(jc); p.setPPr(ppr);
        }
        R r = WML.createR(); Text t = WML.createText(); t.setValue(Optional.ofNullable(text).orElse(""));
        r.getContent().add(t); p.getContent().add(r);
        tc.getContent().add(p);
        return tc;
    }

    private P titleParagraph(String text, int fontSizePt) {
        P p = WML.createP();
        PPr ppr = WML.createPPr(); Jc jc = WML.createJc(); jc.setVal(JcEnumeration.CENTER); ppr.setJc(jc); p.setPPr(ppr);
        R r = WML.createR();
        RPr rpr = WML.createRPr(); BooleanDefaultTrue b = new BooleanDefaultTrue(); b.setVal(true); rpr.setB(b);
        HpsMeasure sz = new HpsMeasure(); sz.setVal(BigInteger.valueOf(fontSizePt*2L)); rpr.setSz(sz); rpr.setSzCs(sz);
        r.setRPr(rpr);
        Text t = WML.createText(); t.setValue(text);
        r.getContent().add(t); p.getContent().add(r);
        return p;
    }
    private P centerParagraph(String text, int fontSizePt) {
        P p = WML.createP();
        PPr ppr = WML.createPPr(); Jc jc = WML.createJc(); jc.setVal(JcEnumeration.CENTER); ppr.setJc(jc); p.setPPr(ppr);
        R r = WML.createR(); if (fontSizePt>0){ RPr rpr=new RPr(); HpsMeasure sz=new HpsMeasure(); sz.setVal(BigInteger.valueOf(fontSizePt*2L)); rpr.setSz(sz); rpr.setSzCs(sz); r.setRPr(rpr); }
        Text t = WML.createText(); t.setValue(text);
        r.getContent().add(t); p.getContent().add(r);
        return p;
    }
    private P emptyLine() { return centerParagraph(" ", 1); }
    private P pageBreakParagraph() {
        P p = WML.createP();
        R r = WML.createR();
        Br br = WML.createBr(); br.setType(STBrType.PAGE);
        r.getContent().add(br);
        p.getContent().add(r);
        return p;
    }

    // ========================================================================
    // I/O helpers
    // ========================================================================

    /** Hỗ trợ data: URL, http(s), file path, và đường dẫn relative trong uploads/signatures */
    private byte[] loadImageBytes(String ref) {
        if (ref == null || ref.isBlank()) return null;
        try {
            // data URL
            if (ref.startsWith("data:image/")) {
                int comma = ref.indexOf(',');
                if (comma > 0) return Base64.getDecoder().decode(ref.substring(comma + 1));
                return null;
            }
            // raw base64
            if (looksLikeBase64(ref)) {
                return Base64.getDecoder().decode(ref);
            }
            // http(s)
            if (ref.startsWith("http://") || ref.startsWith("https://")) {
                try (InputStream in = new java.net.URL(ref).openStream();
                     ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                    in.transferTo(bos);
                    return bos.toByteArray();
                }
            }

            // filesystem candidates
            Path userDir = Paths.get(System.getProperty("user.dir"));
            Path sigDirAbs = userDir.resolve(signatureStorageDir).normalize(); // <user.dir>/uploads/signatures

            List<Path> candidates = new ArrayList<>();
            Path asGiven = Paths.get(ref);
            candidates.add(asGiven.normalize());
            candidates.add(userDir.resolve(asGiven).normalize());
            candidates.add(sigDirAbs.resolve(ref).normalize());
            candidates.add(userDir.resolve("uploads").resolve(ref).normalize());

            if (ref.startsWith("/uploads/")) {
                candidates.add(userDir.resolve(ref.substring(1)).normalize());
            }

            for (Path p : candidates) {
                try {
                    if (Files.exists(p)) {
                        byte[] data = Files.readAllBytes(p);
                        if (data.length > 0) {
                            log.info("Loaded signature from {}", p);
                            return data;
                        }
                    }
                } catch (Exception ignore) {}
            }
            log.error("Signature NOT FOUND. Tried:\n{}",
                    candidates.stream().map(Path::toString).collect(Collectors.joining("\n")));
            return null;
        } catch (Exception e) {
            log.error("loadImageBytes error: {}", e.toString(), e);
            return null;
        }
    }

    private boolean looksLikeBase64(String s) {
        if (s == null || s.length() < 16 || s.length() % 4 != 0) return false;
        for (char c : s.toCharArray()) {
            if (!(Character.isLetterOrDigit(c) || c=='+' || c=='/' || c=='=' || c=='\n' || c=='\r')) return false;
        }
        try { Base64.getDecoder().decode(s); return true; }
        catch (IllegalArgumentException e) { return false; }
    }

    private Long extractContractIdFromPath(Path anyPathUnderContractFolder) {
        // Expect: uploads/contracts/{id}/...
        Path parent = anyPathUnderContractFolder.getParent();
        while (parent != null && !parent.getFileName().toString().equals("contracts")) {
            try {
                long id = Long.parseLong(parent.getFileName().toString());
                return id;
            } catch (NumberFormatException ignore) { }
            parent = parent.getParent();
        }
        throw new IllegalArgumentException("Cannot extract contractId from path: " + anyPathUnderContractFolder);
    }

    private Path contractDirOf(Long id) {
        return Paths.get(System.getProperty("user.dir"), UPLOAD_ROOT, String.valueOf(id)).normalize();
    }
    private Path docxPathOf(Long id) { return contractDirOf(id).resolve(DOCX_NAME); }
    private Path pdfPathOf(Long id)  { return contractDirOf(id).resolve(PDF_NAME); }

    // ========================================================================
    // Small utils for Approval mapping/format
    // ========================================================================

    private String firstNonBlank(String... arr){
        for (String s : arr){ if (s!=null && !s.isBlank()) return s; }
        return null;
    }
    private String safe(Supplier<String> s){
        try { return s.get(); } catch (Exception e){ return null; }
    }

    private LocalDateTime invokeDate(ContractApproval a, String getter){
        try { return (LocalDateTime) ContractApproval.class.getMethod(getter).invoke(a); }
        catch (Exception ignore){ return null; }
    }
    private String invokeString(ContractApproval a, String getter){
        try {
            Object v = ContractApproval.class.getMethod(getter).invoke(a);
            return v != null ? v.toString() : null;
        } catch (Exception ignore){ return null; }
    }
    private LocalDateTime getApprovedAt(ContractApproval a){ return invokeDate(a, "getApprovedAt"); }
    private LocalDateTime getUpdatedAt(ContractApproval a){ return invokeDate(a, "getUpdatedAt"); }
    private LocalDateTime getCreatedAt(ContractApproval a){ return invokeDate(a, "getCreatedAt"); }

    private String formatTs(LocalDateTime ts){
        if (ts==null) return "";
        return ts.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
    }
}
