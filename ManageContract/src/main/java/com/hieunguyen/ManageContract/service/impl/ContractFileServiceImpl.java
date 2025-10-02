package com.hieunguyen.ManageContract.service.impl;

import com.hieunguyen.ManageContract.entity.Contract;
import com.hieunguyen.ManageContract.entity.ContractVariableValue;
import com.hieunguyen.ManageContract.service.ContractFileService;
import jakarta.xml.bind.JAXBElement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.docx4j.model.datastorage.migration.VariablePrepare;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.BinaryPartAbstractImage;
import org.docx4j.wml.*;
import org.springframework.stereotype.Service;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;

import java.io.File;
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

    @Override
    public String generateContractFile(Contract contract) {
        try {
            Path dir = Paths.get(ROOT, String.valueOf(contract.getId()));
            Files.createDirectories(dir);

            WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage
                    .load(new File(contract.getTemplate().getFilePath()));

            Map<String, String> variables = contract.getVariableValues().stream()
                    .collect(Collectors.toMap(ContractVariableValue::getVarName, ContractVariableValue::getVarValue));

            VariablePrepare.prepare(wordMLPackage);
            wordMLPackage.getMainDocumentPart().variableReplace(variables);

            Path docxPath = dir.resolve("contract.docx");
            wordMLPackage.save(docxPath.toFile());

            Path pdfPath = dir.resolve("contract.pdf");
            convertToPdf(docxPath, pdfPath);

            return pdfPath.toString();

        } catch (Exception e) {
            throw new RuntimeException("Error generating contract PDF file", e);
        }
    }

    @Override
    public String embedSignatureFromUrl(String filePath, String imageUrl, Integer page, Float x, Float y,
                                        Float widthPx, Float heightPx, String placeholderKey) {
        try {
            String lower = filePath.toLowerCase();

            if (lower.endsWith(".docx")) {
                WordprocessingMLPackage pkg = WordprocessingMLPackage.load(Path.of(filePath).toFile());
                byte[] imageBytes = loadBytes(imageUrl);

                if (imageBytes == null || imageBytes.length == 0)
                    throw new RuntimeException("Không đọc được bytes ảnh chữ ký");

                String ph = buildPlaceholder(placeholderKey);
                boolean replaced = replacePlaceholderWithImage(pkg, ph, imageBytes, widthPx, heightPx);
                if (!replaced) addParagraphWithImage(pkg, imageBytes, widthPx, heightPx);

                pkg.save(Path.of(filePath).toFile());
                Path docx = Path.of(filePath);
                Path pdf = docx.getParent().resolve(replaceExt(docx.getFileName().toString(), ".pdf"));
                convertToPdf(docx, pdf);
                return pdf.toString();
            }

            if (lower.endsWith(".pdf")) {
                Path pdfIn = Path.of(filePath);
                Path pdfOut = pdfIn.getParent().resolve(rewriteNameWithSuffix(pdfIn.getFileName().toString(), "-signed.pdf"));
                byte[] imageBytes = loadBytes(imageUrl);

                if (imageBytes == null || imageBytes.length == 0)
                    throw new RuntimeException("Không đọc được bytes ảnh chữ ký");

                try (PDDocument doc = PDDocument.load(pdfIn.toFile())) {
                    int pageIndex = Math.max(0, (page != null ? page - 1 : 0));
                    if (pageIndex >= doc.getNumberOfPages()) pageIndex = doc.getNumberOfPages() - 1;
                    PDPage pdPage = doc.getPage(pageIndex);
                    PDImageXObject img = PDImageXObject.createFromByteArray(doc, imageBytes, "signature");

                    float w = widthPx != null ? widthPx : DEFAULT_SIG_W;
                    float h = heightPx != null ? heightPx : DEFAULT_SIG_H;
                    float posX = x != null ? x : 72;
                    float posY = y != null ? y : 72;

                    try (PDPageContentStream cs = new PDPageContentStream(doc, pdPage, AppendMode.APPEND, true, true)) {
                        cs.drawImage(img, posX, posY, w, h);
                    }
                    doc.save(pdfOut.toFile());
                }
                return pdfOut.toString();
            }

            if (lower.endsWith(".html") || lower.endsWith(".htm")) {
                String html = Files.readString(Path.of(filePath), StandardCharsets.UTF_8);
                String imgStyle = "";
                if (widthPx != null) imgStyle += "width:" + widthPx + "px;";
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
                Path docx = Path.of(filePath);
                Path pdf = docx.getParent().resolve(replaceExt(docx.getFileName().toString(), ".pdf"));
                convertToPdf(docx, pdf);
                return pdf.toString();
            }

            if (lower.endsWith(".pdf")) {
                Path pdfIn = Path.of(filePath);
                Path pdfOut = pdfIn.getParent().resolve(rewriteNameWithSuffix(pdfIn.getFileName().toString(), "-signed.pdf"));

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
                    doc.save(pdfOut.toFile());
                }
                return pdfOut.toString();
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

    // ================= DOCX -> PDF via LibreOffice CLI ==================
    private void convertToPdf(Path inputDocx, Path outputPdf) {
        try {
            // Chuyển sang absolute path
            Path inputAbs = inputDocx.toAbsolutePath();
            Path outputDir = outputPdf.getParent().toAbsolutePath();

            // Kiểm tra file đầu vào
            if (!Files.exists(inputAbs)) {
                throw new RuntimeException("Input DOCX file not found: " + inputAbs);
            }

            // Tạo thư mục output nếu chưa có
            Files.createDirectories(outputDir);

            // Build command
            ProcessBuilder pb = new ProcessBuilder(
                    "/Applications/LibreOffice.app/Contents/MacOS/soffice",
                    "--headless",
                    "--convert-to", "pdf",
                    "--outdir", outputDir.toString(),
                    inputAbs.toString()
            );

            // Redirect output & error để debug
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // In ra log để debug
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[LibreOffice] " + line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("LibreOffice conversion failed with exit code " + exitCode);
            }

            // Kiểm tra file PDF đã được tạo
            if (!Files.exists(outputPdf)) {
                // LibreOffice sẽ tạo file PDF cùng tên DOCX trong outdir
                Path generatedPdf = outputDir.resolve(
                        replaceExt(inputAbs.getFileName().toString(), ".pdf")
                );
                if (!Files.exists(generatedPdf)) {
                    throw new RuntimeException("PDF file not generated: " + generatedPdf);
                }
                // Di chuyển hoặc rename sang outputPdf nếu cần
                Files.move(generatedPdf, outputPdf, StandardCopyOption.REPLACE_EXISTING);
            }

            System.out.println("PDF generated successfully: " + outputPdf);

        } catch (Exception e) {
            throw new RuntimeException("Convert to PDF failed", e);
        }
    }


    // ================= DOCX helpers =================
    private boolean replacePlaceholderWithStyledText(WordprocessingMLPackage pkg, String placeholder, String text, int fontSizePt, boolean bold) { /*...*/ return true; }
    private boolean replacePlaceholderWithImage(WordprocessingMLPackage pkg, String placeholder, byte[] imageBytes, Float widthPx, Float heightPx) throws Exception { /*...*/ return true; }
    private void addParagraphWithText(WordprocessingMLPackage pkg, String text, int fontSizePt, boolean bold) { /*...*/ }
    private void addParagraphWithImage(WordprocessingMLPackage pkg, byte[] imageBytes, Float widthPx, Float heightPx) throws Exception { /*...*/ }
    private R createStyledRun(String text, int fontSizePt, boolean bold) { /*...*/ return null; }
    private R createImageRun(WordprocessingMLPackage pkg, byte[] bytes, long cxEmu, long cyEmu) throws Exception { /*...*/ return null; }
    private long pxToEmu(float px) { return Math.round(px * 9525f); }
    private Object unwrap(Object o) { return (o instanceof JAXBElement<?> je) ? je.getValue() : o; }
    private String buildPlaceholder(String key) { return key != null && !key.isBlank() ? "{{SIGN:" + key + "}}" : "{{SIGN}}"; }
    private byte[] loadBytes(String imageUrl) { /*...*/ return null; }
    private String appendToBody(String html, String snippet) { return null; }
    private String escapeHtml(String s) { return null; }
    private String replaceExt(String name, String newExt) { return null; }
    private String rewriteNameWithSuffix(String name, String suffix) { return null; }
}
