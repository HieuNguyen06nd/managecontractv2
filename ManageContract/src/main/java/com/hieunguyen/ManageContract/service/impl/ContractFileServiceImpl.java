package com.hieunguyen.ManageContract.service.impl;

import com.hieunguyen.ManageContract.entity.Contract;
import com.hieunguyen.ManageContract.entity.ContractVariableValue;
import com.hieunguyen.ManageContract.service.ContractFileService;
import jakarta.xml.bind.JAXBElement;
import lombok.extern.slf4j.Slf4j;
import org.docx4j.model.datastorage.migration.VariablePrepare;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.BinaryPartAbstractImage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.wml.*;
import org.springframework.stereotype.Service;

import java.io.File;
import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ContractFileServiceImpl implements ContractFileService {

    private static final ObjectFactory WML = new ObjectFactory();

    public String generateContractFile(Contract contract) {
        try {
            // Load template file
            WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage
                    .load(new File(contract.getTemplate().getFilePath()));

            // Map biến từ DB
            Map<String, String> variables = contract.getVariableValues().stream()
                    .collect(Collectors.toMap(
                            ContractVariableValue::getVarName,
                            ContractVariableValue::getVarValue
                    ));

            // Replace ${varName} bằng giá trị
            VariablePrepare.prepare(wordMLPackage);
            wordMLPackage.getMainDocumentPart().variableReplace(variables);

            // Đường dẫn file mới
            String outputPath = "uploads/contracts/contract_" + contract.getId() + ".docx";

            // Lưu file
            wordMLPackage.save(new File(outputPath));

            return outputPath;

        } catch (Exception e) {
            throw new RuntimeException("Error generating contract file", e);
        }
    }
    @Override
    public String embedSignatureFromUrl(String filePath, String imageUrl,
                                        Integer page, Float x, Float y,
                                        Float widthPx, Float heightPx,
                                        String placeholderKey) {
        try {
            if (filePath.toLowerCase().endsWith(".docx")) {
                WordprocessingMLPackage pkg = WordprocessingMLPackage.load(Path.of(filePath).toFile());
                byte[] imageBytes = loadBytes(imageUrl);
                if (imageBytes == null || imageBytes.length == 0) {
                    throw new RuntimeException("Không đọc được bytes ảnh chữ ký");
                }

                String ph = buildPlaceholder(placeholderKey);
                boolean replaced = replacePlaceholderWithImage(pkg, ph, imageBytes, widthPx, heightPx);

                if (!replaced) {
                    // fallback: chèn xuống cuối tài liệu
                    addParagraphWithImage(pkg, imageBytes, widthPx, heightPx);
                }

                pkg.save(Path.of(filePath).toFile());
                return filePath;
            }

            // HTML fallback (nếu bạn vẫn có template HTML đâu đó)
            if (filePath.toLowerCase().endsWith(".html") || filePath.toLowerCase().endsWith(".htm")) {
                String html = Files.readString(Path.of(filePath), StandardCharsets.UTF_8);
                String imgStyle = "";
                if (widthPx != null)  imgStyle += "width:" + widthPx + "px;";
                if (heightPx != null) imgStyle += "height:" + heightPx + "px;";
                String imgTag = "<img src=\"" + imageUrl + "\" style=\"" + imgStyle + "\" alt=\"signature\"/>";

                String ph = buildPlaceholder(placeholderKey);
                if (html.contains(ph)) html = html.replace(ph, imgTag);
                else html = appendToBody(html, imgTag);

                Files.writeString(Path.of(filePath), html, StandardCharsets.UTF_8);
                return filePath;
            }

            log.warn("embedSignatureFromUrl hiện hỗ trợ DOCX/HTML. File: {}", filePath);
            return filePath;
        } catch (Exception e) {
            throw new RuntimeException("Embed signature (image) failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String embedSignatureText(String filePath, String signerName,
                                     Integer page, Float x, Float y,
                                     int fontSizePt, boolean bold,
                                     String placeholderKey) {
        try {
            if (filePath.toLowerCase().endsWith(".docx")) {
                WordprocessingMLPackage pkg = WordprocessingMLPackage.load(Path.of(filePath).toFile());

                String ph = buildPlaceholder(placeholderKey);
                boolean replaced = replacePlaceholderWithStyledText(pkg, ph, "    " + signerName, fontSizePt, bold);

                if (!replaced) {
                    // fallback: thêm đoạn text cuối tài liệu
                    addParagraphWithText(pkg, "    " + signerName, fontSizePt, bold);
                }

                pkg.save(Path.of(filePath).toFile());
                return filePath;
            }

            // HTML fallback
            if (filePath.toLowerCase().endsWith(".html") || filePath.toLowerCase().endsWith(".htm")) {
                String html = Files.readString(Path.of(filePath), StandardCharsets.UTF_8);
                String style = "font-size:" + fontSizePt + "pt;font-weight:" + (bold ? "700" : "400") + ";";
                String span = "<span style=\"" + style + "\">" + escapeHtml("    " + signerName) + "</span>";
                String ph = buildPlaceholder(placeholderKey);
                if (html.contains(ph)) html = html.replace(ph, span);
                else html = appendToBody(html, span);

                Files.writeString(Path.of(filePath), html, StandardCharsets.UTF_8);
                return filePath;
            }

            log.warn("embedSignatureText hiện hỗ trợ DOCX/HTML. File: {}", filePath);
            return filePath;
        } catch (Exception e) {
            throw new RuntimeException("Embed signature (text) failed: " + e.getMessage(), e);
        }
    }

    // ================= DOCX helpers =================

    private boolean replacePlaceholderWithStyledText(WordprocessingMLPackage pkg,
                                                     String placeholder,
                                                     String text,
                                                     int fontSizePt,
                                                     boolean bold) {
        boolean[] found = {false};

        var mdp = pkg.getMainDocumentPart();
        List<Object> content = mdp.getContent();
        for (int i = 0; i < content.size(); i++) {
            Object obj = unwrap(content.get(i));
            if (obj instanceof P p) {
                for (int rIdx = 0; rIdx < p.getContent().size(); rIdx++) {
                    Object rObj = unwrap(p.getContent().get(rIdx));
                    if (rObj instanceof R r) {
                        for (int tIdx = 0; tIdx < r.getContent().size(); tIdx++) {
                            Object tObj = unwrap(r.getContent().get(tIdx));
                            if (tObj instanceof Text t) {
                                if (placeholder.equals(t.getValue())) {
                                    // thay toàn bộ run bằng text có style
                                    R newRun = createStyledRun(text, fontSizePt, bold);
                                    p.getContent().set(rIdx, newRun);
                                    found[0] = true;
                                    break;
                                }
                            }
                        }
                        if (found[0]) break;
                    }
                }
            }
            if (found[0]) break;
        }
        return found[0];
    }

    private boolean replacePlaceholderWithImage(WordprocessingMLPackage pkg,
                                                String placeholder,
                                                byte[] imageBytes,
                                                Float widthPx,
                                                Float heightPx) throws Exception {
        boolean[] found = {false};

        long cx = widthPx  != null ? pxToEmu(widthPx)  : pxToEmu(180f);
        long cy = heightPx != null ? pxToEmu(heightPx) : pxToEmu(60f);

        var mdp = pkg.getMainDocumentPart();
        List<Object> content = mdp.getContent();

        for (int i = 0; i < content.size(); i++) {
            Object obj = unwrap(content.get(i));
            if (obj instanceof P p) {
                for (int rIdx = 0; rIdx < p.getContent().size(); rIdx++) {
                    Object rObj = unwrap(p.getContent().get(rIdx));
                    if (rObj instanceof R r) {
                        for (int tIdx = 0; tIdx < r.getContent().size(); tIdx++) {
                            Object tObj = unwrap(r.getContent().get(tIdx));
                            if (tObj instanceof Text t) {
                                if (placeholder.equals(t.getValue())) {
                                    // tạo run chứa ảnh
                                    R imageRun = createImageRun(pkg, imageBytes, cx, cy);
                                    p.getContent().set(rIdx, imageRun);
                                    found[0] = true;
                                    break;
                                }
                            }
                        }
                        if (found[0]) break;
                    }
                }
            }
            if (found[0]) break;
        }
        return found[0];
    }

    private void addParagraphWithText(WordprocessingMLPackage pkg,
                                      String text, int fontSizePt, boolean bold) {
        P p = WML.createP();
        p.getContent().add(createStyledRun(text, fontSizePt, bold));
        pkg.getMainDocumentPart().addObject(p);
    }

    private void addParagraphWithImage(WordprocessingMLPackage pkg,
                                       byte[] imageBytes, Float widthPx, Float heightPx) throws Exception {
        long cx = widthPx  != null ? pxToEmu(widthPx)  : pxToEmu(180f);
        long cy = heightPx != null ? pxToEmu(heightPx) : pxToEmu(60f);
        P p = WML.createP();
        p.getContent().add(createImageRun(pkg, imageBytes, cx, cy));
        pkg.getMainDocumentPart().addObject(p);
    }

    private R createStyledRun(String text, int fontSizePt, boolean bold) {
        R r = WML.createR();
        Text t = WML.createText();
        t.setValue(text);
        r.getContent().add(t);

        RPr rPr = WML.createRPr();
        if (bold) {
            BooleanDefaultTrue b = new BooleanDefaultTrue();
            rPr.setB(b);
        }
        HpsMeasure sz = new HpsMeasure();
        sz.setVal(BigInteger.valueOf(fontSizePt * 2L)); // docx4j dùng half-points
        rPr.setSz(sz);
        rPr.setSzCs(sz);
        r.setRPr(rPr);
        return r;
    }

    private R createImageRun(WordprocessingMLPackage pkg, byte[] bytes, long cxEmu, long cyEmu) throws Exception {
        BinaryPartAbstractImage imagePart = BinaryPartAbstractImage.createImagePart(pkg, bytes);
        // docx4j cần ID và tên; đặt tuỳ ý
        org.docx4j.dml.wordprocessingDrawing.Inline inline =
                imagePart.createImageInline("signatureId", "signature", 1, 2, cxEmu, cyEmu, false);

        Drawing drawing = WML.createDrawing();
        drawing.getAnchorOrInline().add(inline);

        R r = WML.createR();
        r.getContent().add(drawing);
        return r;
    }

    private long pxToEmu(float px) {
        // 96 DPI -> 1 px = 9525 EMU
        return Math.round(px * 9525f);
    }

    private Object unwrap(Object o) {
        return (o instanceof JAXBElement<?> je) ? je.getValue() : o;
    }

    // ================= Generic helpers =================

    private String buildPlaceholder(String key) {
        // hỗ trợ 2 kiểu: {{SIGN:KEY}} hoặc ${SIGN:KEY}
        return key != null && !key.isBlank() ? "{{SIGN:" + key + "}}" : "{{SIGN}}";
    }

    private byte[] loadBytes(String imageUrl) {
        try {
            if (imageUrl == null) return null;

            // data URL: data:image/png;base64,xxxx
            if (imageUrl.startsWith("data:")) {
                int comma = imageUrl.indexOf(',');
                String base64 = comma >= 0 ? imageUrl.substring(comma + 1) : imageUrl;
                return Base64.getDecoder().decode(base64);
            }

            // HTTP(S)
            if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
                try (var is = new URL(imageUrl).openStream()) {
                    return is.readAllBytes();
                }
            }

            // File path tuyệt đối/tương đối trên server
            Path p = Path.of(imageUrl);
            if (Files.exists(p)) return Files.readAllBytes(p);

            log.warn("Không tìm thấy ảnh chữ ký: {}", imageUrl);
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Đọc ảnh chữ ký lỗi: " + e.getMessage(), e);
        }
    }

    private String appendToBody(String html, String snippet) {
        int idx = html.lastIndexOf("</body>");
        if (idx >= 0) return html.substring(0, idx) + "\n" + snippet + "\n" + html.substring(idx);
        return html + "\n" + snippet;
    }

    private String escapeHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
