package com.hieunguyen.ManageContract.service.impl;

import com.hieunguyen.ManageContract.entity.Contract;
import com.hieunguyen.ManageContract.entity.ContractTemplate;
import com.hieunguyen.ManageContract.entity.ContractVariableValue;
import com.hieunguyen.ManageContract.repository.ContractRepository;
import com.hieunguyen.ManageContract.repository.ContractTemplateRepository;
import com.hieunguyen.ManageContract.service.ContractFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class ContractFileServiceImpl implements ContractFileService {

    private final ContractRepository contractRepository;
    private final ContractTemplateRepository templateRepository;

    @Override
    public String generateContractFile(Contract contract) {
        try {
            List<ContractVariableValue> variableValues = contract.getVariableValues();
            return generateContractFileWithVariables(contract, variableValues);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi generate contract file", e);
        }
    }

    @Override
    public String generateContractFileWithVariables(Contract contract, List<ContractVariableValue> variableValues) {
        try {
            Path contractDir = Paths.get("uploads", "contracts", String.valueOf(contract.getId()));
            Files.createDirectories(contractDir);

            Path pdfPath = contractDir.resolve("contract.pdf");

            // Tạo file PDF với giá trị biến và các placeholder chữ ký
            createPdfWithSignaturePlaceholders(pdfPath, contract, variableValues);

            return pdfPath.toString();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi generate contract file with variables", e);
        }
    }

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

    @Override
    public String embedSignature(String filePath, String imageUrl, String placeholder) {
        try {
            if (placeholder == null || placeholder.isBlank()) {
                throw new RuntimeException("Placeholder is required for signature embedding");
            }

            Path pdfPath = Path.of(filePath);
            Path tempPdf = pdfPath.getParent().resolve("temp_signed_" + System.currentTimeMillis() + ".pdf");

            try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
                byte[] imageBytes = loadImageBytes(imageUrl);
                if (imageBytes == null) {
                    throw new RuntimeException("Cannot load signature image");
                }

                PDImageXObject image = PDImageXObject.createFromByteArray(document, imageBytes, "signature");

                // TÌM VÀ THAY THẾ PLACEHOLDER TRONG PDF
                boolean replaced = findAndReplacePlaceholder(document, placeholder, image);

                if (!replaced) {
                    log.warn("Placeholder {} not found in PDF, using default position", placeholder);
                    // Nếu không tìm thấy placeholder, thêm vào vị trí mặc định
                    addSignatureToDefaultPosition(document, image, placeholder);
                }

                document.save(tempPdf.toFile());
            }

            Files.move(tempPdf, pdfPath, StandardCopyOption.REPLACE_EXISTING);
            return filePath;

        } catch (Exception e) {
            throw new RuntimeException("Embed signature failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void addApprovalText(String filePath, String approvalText) {
        try {
            Path pdfPath = Path.of(filePath);
            Path tempPdf = pdfPath.getParent().resolve("temp_approved_" + System.currentTimeMillis() + ".pdf");

            try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
                PDFont font = loadUnicodeFont(document);

                PDPage lastPage = document.getPage(document.getNumberOfPages() - 1);
                addTextToPage(document, lastPage, font, approvalText, 50, 50);

                document.save(tempPdf.toFile());
            }

            Files.move(tempPdf, pdfPath, StandardCopyOption.REPLACE_EXISTING);

        } catch (Exception e) {
            throw new RuntimeException("Add approval text failed: " + e.getMessage(), e);
        }
    }

    /**
     * Tạo PDF với các placeholder chữ ký được định nghĩa trong template/flow
     */
    private void createPdfWithSignaturePlaceholders(Path pdfPath, Contract contract, List<ContractVariableValue> variableValues) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDFont unicodeFont = loadUnicodeFont(document);

            Map<String, String> variableMap = new HashMap<>();
            for (ContractVariableValue variable : variableValues) {
                variableMap.put(variable.getVarName(), variable.getVarValue() != null ? variable.getVarValue() : "");
            }

            // Tách phần tạo content stream ra thành phương thức riêng để tránh gán lại biến
            createDocumentContent(document, page, unicodeFont, contract, variableMap, variableValues);

            document.save(pdfPath.toFile());
            log.info("Created PDF with signature placeholders at: {}", pdfPath);

        } catch (Exception e) {
            throw new RuntimeException("Failed to create PDF with signature placeholders", e);
        }
    }

    /**
     * Tạo nội dung document - tách riêng để tránh lỗi reassign contentStream
     */
    private void createDocumentContent(PDDocument document, PDPage firstPage, PDFont unicodeFont,
                                       Contract contract, Map<String, String> variableMap,
                                       List<ContractVariableValue> variableValues) throws IOException {
        PDPage currentPage = firstPage;

        try (PDPageContentStream contentStream = new PDPageContentStream(document, currentPage)) {
            // Tiêu đề hợp đồng
            contentStream.beginText();
            contentStream.setFont(unicodeFont, 16);
            contentStream.newLineAtOffset(100, 750);
            String title = "HỢP ĐỒNG: " + (contract.getTitle() != null ? contract.getTitle() : "Không có tiêu đề");
            contentStream.showText(title);
            contentStream.endText();

            // Số hợp đồng
            contentStream.beginText();
            contentStream.setFont(unicodeFont, 12);
            contentStream.newLineAtOffset(100, 720);
            String contractNumber = "Số hợp đồng: " + (contract.getContractNumber() != null ? contract.getContractNumber() : "N/A");
            contentStream.showText(contractNumber);
            contentStream.endText();

            // Nội dung hợp đồng với các biến
            contentStream.beginText();
            contentStream.setFont(unicodeFont, 12);
            contentStream.newLineAtOffset(100, 670);
            contentStream.showText("NỘI DUNG HỢP ĐỒNG:");
            contentStream.endText();

            // Hiển thị các biến
            int yPosition = 640;
            contentStream.beginText();
            contentStream.setFont(unicodeFont, 10);
            contentStream.newLineAtOffset(100, yPosition);

            for (ContractVariableValue variable : variableValues) {
                if (yPosition < 200) { // Dừng sớm hơn để chỗ cho chữ ký
                    contentStream.endText();
                    contentStream.close(); // Đóng content stream hiện tại

                    // Tạo trang mới
                    currentPage = new PDPage(PDRectangle.A4);
                    document.addPage(currentPage);

                    // Mở content stream mới cho trang mới (không gán lại biến cũ)
                    PDPageContentStream newContentStream = new PDPageContentStream(document, currentPage);
                    newContentStream.beginText();
                    newContentStream.setFont(unicodeFont, 10);
                    newContentStream.newLineAtOffset(100, 750);

                    // Tiếp tục xử lý với content stream mới
                    processVariablesOnNewPage(variableValues, variable, newContentStream, unicodeFont);

                    newContentStream.endText();
                    newContentStream.close();
                    return; // Kết thúc sau khi xử lý trang mới
                }

                String line = "• " + variable.getVarName() + ": " + variable.getVarValue();
                float textWidth = unicodeFont.getStringWidth(line) / 1000 * 10;
                if (textWidth > 400) {
                    processLongText(line, contentStream, unicodeFont);
                } else {
                    contentStream.showText(line);
                    contentStream.newLineAtOffset(0, -15);
                    yPosition -= 15;
                }
            }
            contentStream.endText();

        } // contentStream tự động đóng ở đây

        // Thêm các placeholder chữ ký sau khi đã đóng content stream
        addSignaturePlaceholders(document, currentPage, unicodeFont, variableMap);
    }

    /**
     * Xử lý văn bản dài (xuống dòng)
     */
    private void processLongText(String text, PDPageContentStream contentStream, PDFont font) throws IOException {
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine + word + " ";
            float testWidth = font.getStringWidth(testLine) / 1000 * 10;

            if (testWidth > 400 && !currentLine.isEmpty()) {
                contentStream.showText(currentLine.toString());
                contentStream.newLineAtOffset(0, -15);
                currentLine = new StringBuilder(word + " ");
            } else {
                currentLine.append(word).append(" ");
            }
        }

        if (!currentLine.isEmpty()) {
            contentStream.showText(currentLine.toString());
            contentStream.newLineAtOffset(0, -15);
        }
    }

    /**
     * Xử lý các biến trên trang mới
     */
    private void processVariablesOnNewPage(List<ContractVariableValue> variableValues,
                                           ContractVariableValue currentVariable,
                                           PDPageContentStream contentStream,
                                           PDFont font) throws IOException {
        // Tìm vị trí của biến hiện tại trong danh sách
        int currentIndex = variableValues.indexOf(currentVariable);
        if (currentIndex == -1) return;

        // Tiếp tục xử lý từ biến hiện tại trở đi
        for (int i = currentIndex; i < variableValues.size(); i++) {
            ContractVariableValue variable = variableValues.get(i);
            String line = "• " + variable.getVarName() + ": " + variable.getVarValue();
            float textWidth = font.getStringWidth(line) / 1000 * 10;

            if (textWidth > 400) {
                processLongText(line, contentStream, font);
            } else {
                contentStream.showText(line);
                contentStream.newLineAtOffset(0, -15);
            }
        }
    }

    /**
     * Thêm các placeholder chữ ký dựa trên các biến có tiền tố "SIGN_"
     */
    private void addSignaturePlaceholders(PDDocument document, PDPage page, PDFont font, Map<String, String> variableMap) throws IOException {
        try (PDPageContentStream contentStream = new PDPageContentStream(
                document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {

            int signatureY = 150; // Bắt đầu từ vị trí Y
            int signatureIndex = 0;

            for (Map.Entry<String, String> entry : variableMap.entrySet()) {
                if (entry.getKey().startsWith("SIGN_")) {
                    if (signatureY < 50) {
                        // Nếu hết chỗ, không xử lý thêm - có thể tạo trang mới nếu cần
                        break;
                    }

                    String placeholderName = entry.getKey();
                    String currentValue = entry.getValue();

                    // Tính vị trí X dựa trên index để phân bố đều
                    float xPosition = (signatureIndex % 2 == 0) ? 100 : 400;

                    contentStream.beginText();
                    contentStream.setFont(font, 10);
                    contentStream.newLineAtOffset(xPosition, signatureY);

                    // Hiển thị tên placeholder và giá trị hiện tại (nếu có)
                    String displayText = placeholderName + ": " +
                            (currentValue.isEmpty() ? "[CHỜ KÝ]" : currentValue);
                    contentStream.showText(displayText);
                    contentStream.endText();

                    // Vẽ khung chữ ký
                    drawSignatureBox(contentStream, xPosition, signatureY - 20, 200, 40);

                    signatureY -= 80; // Khoảng cách giữa các chữ ký
                    signatureIndex++;
                }
            }
        }
    }

    /**
     * Vẽ khung cho chữ ký
     */
    private void drawSignatureBox(PDPageContentStream contentStream, float x, float y, float width, float height) throws IOException {
        contentStream.setLineWidth(1f);
        contentStream.addRect(x, y, width, height);
        contentStream.stroke();
    }

    /**
     * Tìm và thay thế placeholder bằng chữ ký
     */
    private boolean findAndReplacePlaceholder(PDDocument document, String placeholder, PDImageXObject image) {
        try {
            Map<String, float[]> placeholderPositions = getPlaceholderPositions(document);

            if (placeholderPositions.containsKey(placeholder)) {
                float[] position = placeholderPositions.get(placeholder);
                PDPage page = document.getPage(0);
                addImageToPage(document, page, image, position[0], position[1], 120, 60);
                return true;
            }

            return false;

        } catch (Exception e) {
            log.error("Error finding and replacing placeholder: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Lấy vị trí của các placeholder trong PDF
     */
    private Map<String, float[]> getPlaceholderPositions(PDDocument document) {
        Map<String, float[]> positions = new HashMap<>();

        // Giả sử chúng ta có các placeholder với vị trí cố định
        positions.put("SIGN_CUSTOMER", new float[]{100, 150});
        positions.put("SIGN_PROVIDER", new float[]{400, 150});
        positions.put("SIGN_MANAGER", new float[]{100, 70});
        positions.put("SIGN_DIRECTOR", new float[]{400, 70});
        positions.put("SIGN_WITNESS1", new float[]{100, 230});
        positions.put("SIGN_WITNESS2", new float[]{400, 230});

        return positions;
    }

    /**
     * Thêm chữ ký vào vị trí mặc định khi không tìm thấy placeholder
     */
    private void addSignatureToDefaultPosition(PDDocument document, PDImageXObject image, String placeholder) {
        try {
            PDPage page = document.getPage(0);

            // Vị trí mặc định dựa trên tên placeholder
            float x = 100, y = 100;
            if (placeholder.contains("MANAGER")) {
                x = 100; y = 70;
            } else if (placeholder.contains("DIRECTOR")) {
                x = 400; y = 70;
            } else if (placeholder.contains("CUSTOMER")) {
                x = 100; y = 150;
            } else if (placeholder.contains("PROVIDER")) {
                x = 400; y = 150;
            }

            addImageToPage(document, page, image, x, y, 120, 60);

        } catch (Exception e) {
            log.error("Error adding signature to default position: {}", e.getMessage());
        }
    }

    /**
     * Load font Unicode
     */
    private PDFont loadUnicodeFont(PDDocument document) {
        try {
            // Thử load từ classpath
            try {
                ClassPathResource resource = new ClassPathResource("fonts/arial.ttf");
                if (resource.exists()) {
                    try (InputStream fontStream = resource.getInputStream()) {
                        return PDType0Font.load(document, fontStream);
                    }
                }
            } catch (Exception e) {
                log.debug("Không tìm thấy font trong classpath: {}", e.getMessage());
            }

            // Thử load từ hệ thống
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

            log.warn("Không tìm thấy font Unicode, sử dụng Helvetica");
            return PDType1Font.HELVETICA;

        } catch (Exception e) {
            log.error("Lỗi khi load font Unicode: {}", e.getMessage());
            return PDType1Font.HELVETICA;
        }
    }

    private byte[] loadImageBytes(String imageUrl) {
        try {
            if (imageUrl == null || imageUrl.isBlank()) {
                return null;
            }

            if (imageUrl.startsWith("data:")) {
                String base64Data = imageUrl.substring(imageUrl.indexOf(",") + 1);
                return Base64.getDecoder().decode(base64Data);
            } else {
                Path imagePath = Paths.get(imageUrl);
                if (Files.exists(imagePath)) {
                    return Files.readAllBytes(imagePath);
                }

                imagePath = Paths.get("uploads/signatures", imageUrl);
                if (Files.exists(imagePath)) {
                    return Files.readAllBytes(imagePath);
                }
            }
        } catch (Exception e) {
            log.error("Failed to load image: {}", e.getMessage());
        }
        return null;
    }

    private void addImageToPage(PDDocument document, PDPage page, PDImageXObject image,
                                float x, float y, float width, float height) {
        try (PDPageContentStream contentStream = new PDPageContentStream(
                document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
            contentStream.drawImage(image, x, y, width, height);
        } catch (IOException e) {
            log.error("Error drawing image on page: {}", e.getMessage());
        }
    }

    private void addTextToPage(PDDocument document, PDPage page, PDFont font,
                               String text, float x, float y) {
        try (PDPageContentStream contentStream = new PDPageContentStream(
                document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
            contentStream.beginText();
            contentStream.setFont(font, 8);
            contentStream.newLineAtOffset(x, y);
            contentStream.showText(text);
            contentStream.endText();
        } catch (IOException e) {
            log.error("Error adding text to page: {}", e.getMessage());
        }
    }
}