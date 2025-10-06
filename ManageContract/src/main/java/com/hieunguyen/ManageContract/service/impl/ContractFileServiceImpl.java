package com.hieunguyen.ManageContract.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hieunguyen.ManageContract.entity.Contract;
import com.hieunguyen.ManageContract.entity.ContractVariableValue;
import com.hieunguyen.ManageContract.repository.ContractRepository;
import com.hieunguyen.ManageContract.service.ContractFileService;
import jakarta.xml.bind.JAXBElement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.docx4j.dml.wordprocessingDrawing.Inline;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.BinaryPartAbstractImage;
import org.docx4j.wml.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;

import java.io.*;
import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ContractFileServiceImpl implements ContractFileService {

    private static final ObjectFactory WML = new ObjectFactory();
    private static final String ROOT = "uploads/contracts";
    private static final float DEFAULT_SIG_W = 180f;
    private static final float DEFAULT_SIG_H = 60f;

    private final ContractRepository contractRepository;

    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper om = new ObjectMapper();

    @Value("${app.signature.storage-dir}")
    private String signatureStorageDir;

    private static final String SIGN_STATIC_PREFIX = "/static/signatures/";

    @Value("${app.ds.url:http://localhost:8081}")
    private String docServer; // http://localhost:8081

    @Value("${app.ds.source-base:http://host.docker.internal:8080}")
    private String hostBaseUrl; // http://host.docker.internal:8080

    // ======================================================
    // 1) Generate DOCX từ template + variables
    // ======================================================
    @Override
    public String generateDocxFile(Contract contract) {
        try {
            Path dir = Paths.get(System.getProperty("user.dir"),
                    "uploads","contracts", String.valueOf(contract.getId()));
            Files.createDirectories(dir);

            var wordML = org.docx4j.openpackaging.packages.WordprocessingMLPackage
                    .load(new java.io.File(contract.getTemplate().getFilePath()));

            Map<String,String> map = contract.getVariableValues().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            ContractVariableValue::getVarName,
                            v -> java.util.Optional.ofNullable(v.getVarValue()).orElse("")
                    ));

            org.docx4j.model.datastorage.migration.VariablePrepare.prepare(wordML);
            wordML.getMainDocumentPart().variableReplace(map);

            Path docx = dir.resolve("contract.docx");
            wordML.save(docx.toFile());
            return docx.toString();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi generate DOCX", e);
        }
    }


    @Override
    public String generateContractFile(Contract contract) {
        // 1) Sinh DOCX trước
        String docxPath = generateDocxFile(contract); // đã có sẵn method này
        Path docx = Path.of(docxPath);

        // 2) Convert sang PDF (dùng ONLYOFFICE bạn đã viết)
        Path pdf = docx.getParent().resolve("contract.pdf");
        convertToPdfUsingOnlyOffice(contract.getId(), docx, pdf);

        // 3) Lưu lại filePath (PDF) vào DB nếu muốn
        contract.setFilePath(pdf.toString());
        contractRepository.save(contract);

        // 4) Trả về đường dẫn PDF
        return pdf.toString();
    }


    // ======================================================
    // 2) Trả PDF để xem (convert nếu cần bằng ONLYOFFICE)
    // ======================================================
    @Override
    public java.io.File getPdfOrConvert(Long contractId) {
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

            // update DB nếu bạn muốn lưu path
            Contract c = contractRepository.findById(contractId).orElseThrow();
            c.setFilePath(pdf.toString());
            contractRepository.save(c);

            return pdfPathOf(contractId).toFile();
        } catch (Exception e) {
            throw new RuntimeException("Get PDF or convert failed", e);
        }
    }


    // ======================================================
    // 3) Trả file gốc (có thể là DOCX hoặc PDF)
    // ======================================================
    @Override
    public File getContractFile(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        if (contract.getFilePath() == null) {
            throw new RuntimeException("Contract file not generated yet");
        }

        Path path = Path.of(contract.getFilePath());
        if (!Files.exists(path)) {
            throw new RuntimeException("Contract file not found on server");
        }
        return path.toFile();
    }

    // ======================================================
    // 4) Ký bằng ảnh
    // ======================================================
    @Override
    public String embedSignatureFromUrl(String filePath, String imageUrl, Integer page, Float x, Float y,
                                        Float widthPx, Float heightPx, String placeholderKey) {
        try {
            String lower = filePath.toLowerCase();

            if (lower.endsWith(".docx")) {
                WordprocessingMLPackage pkg = WordprocessingMLPackage.load(Path.of(filePath).toFile());
                byte[] imageBytes = loadBytes(imageUrl);
                if (imageBytes == null || imageBytes.length == 0) {
                    throw new RuntimeException("Không đọc được bytes ảnh chữ ký");
                }

                String ph = buildPlaceholder(placeholderKey);
                boolean replaced = replacePlaceholderWithImage(pkg, ph, imageBytes, widthPx, heightPx);
                if (!replaced) addParagraphWithImage(pkg, imageBytes, widthPx, heightPx);

                pkg.save(Path.of(filePath).toFile());

                // Sau ký, tạo lại PDF (nếu bạn muốn xem/ tải)
                Path docx = Path.of(filePath);
                Long contractId = extractContractIdFromPath(docx);
                Path pdf = docx.getParent().resolve(replaceExt(docx.getFileName().toString(), ".pdf"));

                convertToPdfUsingOnlyOffice(contractId, docx, pdf);

                var c = contractRepository.findById(contractId).orElseThrow();
                c.setFilePath(pdf.toString());
                contractRepository.save(c);

                return pdf.toString();
            }
            if (lower.endsWith(".pdf")) {
                // Vẽ ảnh trực tiếp lên PDF rồi GHI ĐÈ file gốc để FE reload là thấy ngay
                Path pdfIn = Path.of(filePath);
                Path pdfTmp = pdfIn.getParent().resolve(rewriteNameWithSuffix(pdfIn.getFileName().toString(), "-signed.tmp.pdf"));

                byte[] imageBytes = loadBytes(imageUrl);
                if (imageBytes == null || imageBytes.length == 0) {
                    throw new RuntimeException("Không đọc được bytes ảnh chữ ký");
                }

                try (PDDocument doc = PDDocument.load(pdfIn.toFile())) {
                    int pageIndex = Math.max(0, (page != null ? page - 1 : 0));
                    if (pageIndex >= doc.getNumberOfPages()) pageIndex = doc.getNumberOfPages() - 1;
                    PDPage pdPage = doc.getPage(pageIndex);
                    PDImageXObject img = PDImageXObject.createFromByteArray(doc, imageBytes, "signature");

                    float w = widthPx != null ? widthPx : DEFAULT_SIG_W;
                    float h = heightPx != null ? heightPx : DEFAULT_SIG_H;
                    float posX = x != null ? x : 72;  // toạ độ pt/pixel bạn đã quy đổi từ FE
                    float posY = y != null ? y : 72;

                    try (PDPageContentStream cs =
                                 new PDPageContentStream(doc, pdPage, AppendMode.APPEND, true, true)) {
                        cs.drawImage(img, posX, posY, w, h);
                    }
                    doc.save(pdfTmp.toFile());
                }

                overwriteOriginalPdf(pdfTmp, pdfIn); // ghi đè file gốc
                return pdfIn.toString();
            }

            if (lower.endsWith(".html") || lower.endsWith(".htm")) {
                String html = Files.readString(Path.of(filePath), StandardCharsets.UTF_8);
                String imgStyle = "";
                if (widthPx != null)  imgStyle += "width:" + widthPx  + "px;";
                if (heightPx != null) imgStyle += "height:" + heightPx + "px;";
                String imgTag = "<img src=\"" + imageUrl + "\" style=\"" + imgStyle + "\" alt=\"signature\"/>";
                String ph = buildPlaceholder(placeholderKey);
                html = html.contains(ph) ? html.replace(ph, imgTag) : appendToBody(html, imgTag);
                Files.writeString(Path.of(filePath), html, StandardCharsets.UTF_8);
                return filePath;
            }

            log.warn("embedSignatureFromUrl: không hỗ trợ định dạng file: {}", filePath);
            return filePath;

        } catch (Exception e) {
            throw new RuntimeException("Embed signature (image) failed: " + e.getMessage(), e);
        }
    }

    // ======================================================
    // 5) Ký bằng text
    // ======================================================
    @Override
    public String embedSignatureText(String filePath, String signerName, Integer page, Float x, Float y,
                                     int fontSizePt, boolean bold, String placeholderKey) {
        try {
            String lower = filePath.toLowerCase();

            if (lower.endsWith(".docx")) {
                WordprocessingMLPackage pkg = WordprocessingMLPackage.load(Path.of(filePath).toFile());
                String ph = buildPlaceholder(placeholderKey);
                boolean replaced = replacePlaceholderWithStyledText(pkg, ph, "    " + signerName, fontSizePt, bold);
                if (!replaced) addParagraphWithText(pkg, "    " + signerName, fontSizePt, bold);

                pkg.save(Path.of(filePath).toFile());

                // (Giống trên) Tạo lại PDF nếu cần bằng ONLYOFFICE thông qua getPdfOrConvert(contractId)
                Path docx = Path.of(filePath);
                Path pdf = docx.getParent().resolve(replaceExt(docx.getFileName().toString(), ".pdf"));
                return pdf.toString();
            }

            if (lower.endsWith(".pdf")) {
                Path pdfIn = Path.of(filePath);
                Path pdfTmp = pdfIn.getParent().resolve(rewriteNameWithSuffix(pdfIn.getFileName().toString(), "-signed.tmp.pdf"));

                try (PDDocument doc = PDDocument.load(pdfIn.toFile())) {
                    int pageIndex = Math.max(0, (page != null ? page - 1 : 0));
                    if (pageIndex >= doc.getNumberOfPages()) pageIndex = doc.getNumberOfPages() - 1;
                    PDPage pdPage = doc.getPage(pageIndex);

                    float posX = x != null ? x : 72;
                    float posY = y != null ? y : 72;

                    try (PDPageContentStream cs = new PDPageContentStream(doc, pdPage, AppendMode.APPEND, true, true)) {
                        cs.beginText();
                        cs.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD, fontSizePt);
                        cs.newLineAtOffset(posX, posY);
                        cs.showText(signerName);
                        cs.endText();
                    }
                    doc.save(pdfTmp.toFile());
                }

                overwriteOriginalPdf(pdfTmp, pdfIn);
                return pdfIn.toString();
            }

            if (lower.endsWith(".html") || lower.endsWith(".htm")) {
                String html = Files.readString(Path.of(filePath), StandardCharsets.UTF_8);
                String style = "font-size:" + fontSizePt + "pt;font-weight:" + (bold ? "700" : "400") + ";";
                String span = "<span style=\"" + style + "\">" + signerName + "</span>";
                String ph = buildPlaceholder(placeholderKey);
                html = html.contains(ph) ? html.replace(ph, span) : appendToBody(html, span);
                Files.writeString(Path.of(filePath), html, StandardCharsets.UTF_8);
                return filePath;
            }

            log.warn("embedSignatureText: không hỗ trợ định dạng file: {}", filePath);
            return filePath;

        } catch (Exception e) {
            throw new RuntimeException("Embed signature (text) failed: " + e.getMessage(), e);
        }
    }


    // ======================================================
    // Helpers cho DOCX
    // ======================================================
    private boolean replacePlaceholderWithStyledText(WordprocessingMLPackage pkg, String placeholder,
                                                     String text, int fontSizePt, boolean bold) {
        boolean replaced = false;
        var mdp = pkg.getMainDocumentPart();

        List<Object> content = mdp.getContent();
        for (Object pObj : content) {
            Object unwrapped = unwrap(pObj);
            if (unwrapped instanceof P p) {
                List<Object> runs = p.getContent();
                for (int i = 0; i < runs.size(); i++) {
                    Object rObj = unwrap(runs.get(i));
                    if (rObj instanceof R r) {
                        for (Object tObj : r.getContent()) {
                            Object tUnwrapped = unwrap(tObj);
                            if (tUnwrapped instanceof Text t) {
                                String val = t.getValue();
                                if (val != null && val.contains(placeholder)) {
                                    int idx = val.indexOf(placeholder);
                                    String before = val.substring(0, idx);
                                    String after  = val.substring(idx + placeholder.length());

                                    t.setValue(before);
                                    R styled = createStyledRun(text, fontSizePt, bold);
                                    runs.add(i + 1, styled);

                                    if (!after.isEmpty()) {
                                        R afterRun = WML.createR();
                                        Text afterText = WML.createText();
                                        afterText.setValue(after);
                                        afterRun.getContent().add(afterText);
                                        runs.add(i + 2, afterRun);
                                    }
                                    replaced = true;
                                    break;
                                }
                            }
                        }
                    }
                    if (replaced) break;
                }
            }
            if (replaced) break;
        }
        return replaced;
    }

    private void overwriteOriginalPdf(Path pdfTmp, Path pdfIn) throws IOException {
        try {
            Files.move(pdfTmp, pdfIn, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(pdfTmp, pdfIn, StandardCopyOption.REPLACE_EXISTING);
        }
    }


    private boolean replacePlaceholderWithImage(WordprocessingMLPackage pkg, String placeholder,
                                                byte[] imageBytes, Float widthPx, Float heightPx) throws Exception {
        boolean replaced = false;
        var mdp = pkg.getMainDocumentPart();

        long cx = pxToEmu(widthPx != null ? widthPx : DEFAULT_SIG_W);
        long cy = pxToEmu(heightPx != null ? heightPx : DEFAULT_SIG_H);

        List<Object> content = mdp.getContent();
        outer:
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

                                    R imgRun = createImageRun(pkg, imageBytes, cx, cy);
                                    runs.add(ri + 1, imgRun);

                                    if (!after.isEmpty()) {
                                        R afterRun = WML.createR();
                                        Text afterText = WML.createText();
                                        afterText.setValue(after);
                                        afterRun.getContent().add(afterText);
                                        runs.add(ri + 2, afterRun);
                                    }

                                    replaced = true;
                                    break outer;
                                }
                            }
                        }
                    }
                }
            }
        }
        return replaced;
    }

    private void addParagraphWithText(WordprocessingMLPackage pkg, String text, int fontSizePt, boolean bold) {
        P p = WML.createP();
        R r = createStyledRun(text, fontSizePt, bold);
        p.getContent().add(r);
        pkg.getMainDocumentPart().getContent().add(p);
    }

    private void addParagraphWithImage(WordprocessingMLPackage pkg, byte[] imageBytes,
                                       Float widthPx, Float heightPx) throws Exception {
        long cx = pxToEmu(widthPx != null ? widthPx : DEFAULT_SIG_W);
        long cy = pxToEmu(heightPx != null ? heightPx : DEFAULT_SIG_H);

        R imgRun = createImageRun(pkg, imageBytes, cx, cy);
        P p = WML.createP();
        p.getContent().add(imgRun);
        pkg.getMainDocumentPart().getContent().add(p);
    }

    private R createStyledRun(String text, int fontSizePt, boolean bold) {
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
        size.setVal(BigInteger.valueOf((long) (fontSizePt <= 0 ? 20 : fontSizePt * 2L))); // half-points
        rPr.setSz(size);
        rPr.setSzCs(size);

        r.setRPr(rPr);
        r.getContent().add(t);
        return r;
    }

    private R createImageRun(WordprocessingMLPackage pkg, byte[] bytes, long cxEmu, long cyEmu) throws Exception {
        BinaryPartAbstractImage imagePart = BinaryPartAbstractImage.createImagePart(pkg, bytes);
        Inline inline = imagePart.createImageInline("signature", "signature", 0, 1, cxEmu, cyEmu, false);

        org.docx4j.wml.ObjectFactory factory = new org.docx4j.wml.ObjectFactory();
        Drawing drawing = factory.createDrawing();
        drawing.getAnchorOrInline().add(inline);

        R run = factory.createR();
        run.getContent().add(drawing);
        return run;
    }

    private long pxToEmu(float px) { return Math.round(px * 9525f); }
    private Object unwrap(Object o) { return (o instanceof JAXBElement<?> je) ? je.getValue() : o; }
    private String buildPlaceholder(String key) { return (key != null && !key.isBlank()) ? "{{SIGN:" + key + "}}" : "{{SIGN}}"; }

    /** Hỗ trợ: data URL, file path, http(s) */
    private byte[] loadBytes(String imageUrlOrPath) {
        if (imageUrlOrPath == null || imageUrlOrPath.isBlank()) return null;
        try {
            // 1) data:image/png;base64,....
            if (imageUrlOrPath.startsWith("data:")) {
                int comma = imageUrlOrPath.indexOf(',');
                if (comma > 0) {
                    String b64 = imageUrlOrPath.substring(comma + 1);
                    return Base64.getDecoder().decode(b64);
                }
                return null;
            }

            // 2) Public URL /static/signatures/... => quy về file system path
            if (imageUrlOrPath.startsWith(SIGN_STATIC_PREFIX)) {
                Path p = resolveSignatureFsPath(imageUrlOrPath);
                if (Files.exists(p)) return Files.readAllBytes(p);
                throw new FileNotFoundException("Signature not found: " + p);
            }

            // 3) Đường dẫn file thật (absolute hoặc relative)
            Path filePath = Paths.get(imageUrlOrPath);
            if (Files.exists(filePath)) {
                return Files.readAllBytes(filePath);
            }
            // Nếu relative -> thử tương đối với signatureStorageDir
            Path rel = Paths.get(signatureStorageDir, imageUrlOrPath).normalize();
            if (Files.exists(rel)) {
                return Files.readAllBytes(rel);
            }

            // 4) http(s)
            if (imageUrlOrPath.startsWith("http://") || imageUrlOrPath.startsWith("https://")) {
                try (InputStream in = new java.net.URL(imageUrlOrPath).openStream();
                     ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                    in.transferTo(bos);
                    return bos.toByteArray();
                }
            }

            throw new FileNotFoundException("Unsupported/Not found: " + imageUrlOrPath);
        } catch (Exception ex) {
            log.error("loadBytes fail: {}", ex.getMessage(), ex);
            return null;
        }
    }

    private Path resolveSignatureFsPath(String publicOrFsPath) {
        if (publicOrFsPath.startsWith(SIGN_STATIC_PREFIX)) {
            String relative = publicOrFsPath.substring(SIGN_STATIC_PREFIX.length()); // 503/1/xxx.png
            return Paths.get(signatureStorageDir, relative).normalize();
        }
        // Nếu đưa thẳng đường dẫn file thật (absolute/relative), cứ trả về Path tương ứng:
        Path p = Paths.get(publicOrFsPath);
        if (!p.isAbsolute()) {
            // nếu là relative, coi như tương đối so với signatureStorageDir
            p = Paths.get(signatureStorageDir, publicOrFsPath).normalize();
        }
        return p;
    }


    private void convertToPdfUsingOnlyOffice(Long contractId, Path inputDocx, Path outputPdf) {
        try {
            Files.createDirectories(outputPdf.getParent());

            // URL cho ONLYOFFICE tải file DOCX từ BE của bạn (container sẽ gọi ra host.docker.internal)
            final String sourceUrl = hostBaseUrl.replaceAll("/+$", "")
                    + "/internal/files/" + contractId + "/contract.docx";

            // payload JSON
            Map<String, Object> payload = new HashMap<>();
            payload.put("async", false);
            payload.put("filetype", "docx");
            payload.put("outputtype", "pdf");
            payload.put("key", contractId + "-" + System.currentTimeMillis());
            payload.put("title", "contract.docx");
            payload.put("url", sourceUrl);

            String json = om.writeValueAsString(payload);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            // Thử lần lượt 2 path (tùy bản DocumentServer)

            String base = docServer.replaceAll("/+$", "");
            String[] endpoints = {
                    base + "/ConvertService.ashx",
                    base + "/ConvertService"
            };
            ResponseEntity<String> resp = null;
            Exception lastEx = null;
            for (String ep : endpoints) {
                try {
                    resp = rest.postForEntity(ep, new HttpEntity<>(json, headers), String.class);
                    if (resp.getStatusCode().is2xxSuccessful()) {
                        break;
                    }
                } catch (Exception e) {
                    lastEx = e;
                }
            }
            if (resp == null) {
                throw new RuntimeException("ConvertService request failed (no response)", lastEx);
            }
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                throw new RuntimeException("ConvertService HTTP error: " + resp.getStatusCode() +
                        " body=" + (resp.getBody() == null ? "<null>" : resp.getBody()));
            }

            // Có trường hợp DocumentServer trả HTML/XML khi lỗi -> kiểm tra content-type
            MediaType ct = resp.getHeaders().getContentType();
            if (ct == null || !ct.toString().toLowerCase().contains("application/json")) {
                // Nhiều khi trả 200 nhưng body là HTML lỗi (502/Bad Gateway của Nginx)
                throw new RuntimeException("ConvertService response is not JSON. contentType=" + ct +
                        ", body=" + resp.getBody());
            }

            JsonNode root = om.readTree(resp.getBody());

            // Poll đơn giản nếu chưa endConvert
            int attempts = 0;
            while (!root.path("endConvert").asBoolean(false) && attempts < 20) {
                // Gửi lại cùng payload để ONLYOFFICE tiếp tục convert
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

            // Tải PDF kết quả về outputPdf
            byte[] pdfBytes = rest.getForObject(fileUrl, byte[].class);
            if (pdfBytes == null || pdfBytes.length == 0) {
                throw new RuntimeException("Downloaded PDF is empty from: " + fileUrl);
            }

            Files.write(outputPdf, pdfBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            // cập nhật DB
            var c = contractRepository.findById(contractId).orElseThrow();
            c.setFilePath(outputPdf.toString());
            contractRepository.save(c);

        } catch (Exception e) {
            throw new RuntimeException("Convert via ONLYOFFICE failed", e);
        }
    }

    private Path docxPathOf(Long id) {
        return Paths.get(System.getProperty("user.dir"),
                "uploads","contracts", String.valueOf(id), "contract.docx");
    }
    private Path pdfPathOf(Long id) {
        return Paths.get(System.getProperty("user.dir"),
                "uploads","contracts", String.valueOf(id), "contract.pdf");
    }


    private void assertDocServerUp() {
        try {
            String ok = rest.getForObject(docServer + "/healthcheck", String.class);
            if (!"true".equalsIgnoreCase(ok)) {
                throw new IllegalStateException("Document Server /healthcheck != true");
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot reach Document Server at " + docServer, e);
        }
    }

    private Long extractContractIdFromPath(Path file) {
        Path parent = file.getParent();
        if (parent == null) throw new IllegalArgumentException("Invalid path: " + file);
        String idStr = parent.getFileName().toString();
        try { return Long.parseLong(idStr); }
        catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Cannot parse contractId from path: " + file, ex);
        }
    }

    private String appendToBody(String html, String snippet) {
        if (html == null) return snippet;
        int idx = html.toLowerCase().lastIndexOf("</body>");
        if (idx >= 0) return html.substring(0, idx) + snippet + html.substring(idx);
        return html + snippet;
    }

    private String replaceExt(String name, String newExt) {
        int dot = name.lastIndexOf('.');
        return (dot >= 0 ? name.substring(0, dot) : name) + newExt;
    }

    private String rewriteNameWithSuffix(String name, String suffix) {
        int dot = name.lastIndexOf('.');
        return (dot >= 0) ? name.substring(0, dot) + suffix + name.substring(dot) : name + suffix;
    }
}
