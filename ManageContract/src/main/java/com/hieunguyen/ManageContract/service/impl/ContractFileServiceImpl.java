package com.hieunguyen.ManageContract.service.impl;

import com.hieunguyen.ManageContract.entity.*;
import com.hieunguyen.ManageContract.repository.ContractRepository;
import com.hieunguyen.ManageContract.repository.ContractApprovalRepository;
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
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class ContractFileServiceImpl implements ContractFileService {

    private final ContractRepository contractRepository;
    private final ContractApprovalRepository contractApprovalRepository;

    @Override
    public String generateContractFile(Contract contract) {
        if (contract.getFilePath() != null && Files.exists(Path.of(contract.getFilePath()))) {
            return contract.getFilePath();
        }
        throw new RuntimeException("Contract file not found. Please upload contract file first.");
    }

    @Override
    public String generateContractFileWithVariables(Contract contract, List<ContractVariableValue> variableValues) {
        if (contract.getFilePath() != null && Files.exists(Path.of(contract.getFilePath()))) {
            return contract.getFilePath();
        }
        throw new RuntimeException("Contract file not found. Please upload contract file first.");
    }

    @Override
    public File getContractFile(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        if (contract.getFilePath() == null) {
            throw new RuntimeException("Contract file not found");
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

                // TÌM VÀ THAY THẾ PLACEHOLDER TRONG PDF DỰA TRÊN TEXT
                boolean replaced = findAndReplacePlaceholderByText(document, placeholder, image);

                if (!replaced) {
                    log.warn("Placeholder {} not found in PDF", placeholder);
                    throw new RuntimeException("Signature placeholder '" + placeholder + "' not found in contract");
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
    public String embedSignatureForApproval(Long contractId, String imageUrl, Long approvalId) {
        try {
            ContractApproval approval = contractApprovalRepository.findById(approvalId)
                    .orElseThrow(() -> new RuntimeException("Contract approval not found"));

            if (approval.getSignaturePlaceholder() == null || approval.getSignaturePlaceholder().isBlank()) {
                throw new RuntimeException("No signature placeholder defined for this approval step");
            }

            Contract contract = approval.getContract();
            if (contract.getFilePath() == null) {
                throw new RuntimeException("Contract file not found");
            }

            return embedSignature(contract.getFilePath(), imageUrl, approval.getSignaturePlaceholder());

        } catch (Exception e) {
            throw new RuntimeException("Embed signature for approval failed: " + e.getMessage(), e);
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

    @Override
    public List<String> getSignaturePlaceholders(Long contractId) {
        List<ContractApproval> approvals = contractApprovalRepository.findByContractId(contractId);
        List<String> placeholders = new ArrayList<>();

        for (ContractApproval approval : approvals) {
            if (approval.getSignaturePlaceholder() != null &&
                    !approval.getSignaturePlaceholder().isBlank()) {
                placeholders.add(approval.getSignaturePlaceholder());
            }
        }

        return placeholders;
    }

    @Override
    public boolean validatePlaceholdersInContract(Long contractId) {
        try {
            Contract contract = contractRepository.findById(contractId)
                    .orElseThrow(() -> new RuntimeException("Contract not found"));

            if (contract.getFilePath() == null) {
                throw new RuntimeException("Contract file not found");
            }

            List<String> placeholders = getSignaturePlaceholders(contractId);
            if (placeholders.isEmpty()) {
                log.warn("No signature placeholders found for contract {}", contractId);
                return false;
            }

            try (PDDocument document = PDDocument.load(new File(contract.getFilePath()))) {
                String pdfText = extractAllText(document);

                for (String placeholder : placeholders) {
                    if (!pdfText.contains(placeholder)) {
                        log.warn("Placeholder '{}' not found in contract file", placeholder);
                        return false;
                    }
                }
            }

            log.info("All {} placeholders validated successfully for contract {}", placeholders.size(), contractId);
            return true;

        } catch (Exception e) {
            log.error("Error validating placeholders for contract {}: {}", contractId, e.getMessage());
            return false;
        }
    }

    /**
     * Tìm và thay thế placeholder bằng chữ ký dựa trên text trong PDF
     */
    private boolean findAndReplacePlaceholderByText(PDDocument document, String placeholder, PDImageXObject image) {
        try {
            // Tìm tất cả các trang có chứa placeholder
            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                PDPage page = document.getPage(pageIndex);
                Map<String, TextPosition> placeholderPositions = findTextPosition(document, page, placeholder);

                if (!placeholderPositions.isEmpty()) {
                    for (Map.Entry<String, TextPosition> entry : placeholderPositions.entrySet()) {
                        TextPosition pos = entry.getValue();
                        float x = pos.getXDirAdj();
                        float y = pos.getYDirAdj();
                        float width = pos.getWidthDirAdj();
                        float height = pos.getHeightDir();

                        replaceTextWithImage(document, page, image, x, y, width, height);
                    }
                    return true;
                }
            }

            return false;

        } catch (Exception e) {
            log.error("Error finding and replacing placeholder by text: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Tìm vị trí của text trong trang PDF
     */
    private Map<String, TextPosition> findTextPosition(PDDocument document, PDPage page, String searchText) throws IOException {
        Map<String, TextPosition> positions = new HashMap<>();

        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
                for (TextPosition position : textPositions) {
                    String textSegment = position.getUnicode();
                    if (textSegment != null && textSegment.contains(searchText)) {
                        positions.put(textSegment, position);
                    }
                }
                super.writeString(text, textPositions);
            }
        };

        stripper.setStartPage(document.getPages().indexOf(page) + 1);
        stripper.setEndPage(document.getPages().indexOf(page) + 1);
        stripper.getText(document);

        return positions;
    }

    /**
     * Trích xuất toàn bộ text từ PDF để validate
     */
    private String extractAllText(PDDocument document) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        return stripper.getText(document);
    }

    /**
     * Thay thế text bằng hình ảnh
     */
    private void replaceTextWithImage(PDDocument document, PDPage page, PDImageXObject image,
                                      float x, float y, float width, float height) {
        try (PDPageContentStream contentStream = new PDPageContentStream(
                document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {

            // Tính toán vị trí Y (PDFBox coordinates are from bottom left)
            float pageHeight = page.getMediaBox().getHeight();
            float actualY = pageHeight - y - height;

            // Vẽ hình chữ nhật trắng để che text cũ
            contentStream.setNonStrokingColor(255, 255, 255);
            contentStream.addRect(x, actualY, width + 10, height + 5);
            contentStream.fill();

            // Vẽ chữ ký với kích thước phù hợp
            float imageWidth = Math.max(width * 2, 120); // Rộng gấp 2 lần text, tối thiểu 120
            float imageHeight = Math.max(height * 3, 60); // Cao gấp 3 lần text, tối thiểu 60

            contentStream.drawImage(image, x, actualY - 5, imageWidth, imageHeight);

        } catch (IOException e) {
            log.error("Error replacing text with image: {}", e.getMessage());
        }
    }

    /**
     * Load font Unicode
     */
    private PDFont loadUnicodeFont(PDDocument document) {
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

        String[] fontPaths = {
                "C:/Windows/Fonts/arial.ttf",
                "/usr/share/fonts/truetype/freefont/FreeSans.ttf",
                "/Library/Fonts/Arial.ttf"
        };

        for (String fontPath : fontPaths) {
            File fontFile = new File(fontPath);
            if (fontFile.exists()) {
                try {
                    return PDType0Font.load(document, fontFile);
                } catch (IOException ex) {
                    log.warn("Cannot load font from {}", fontPath);
                }
            }
        }

        log.warn("Không tìm thấy font Unicode, sử dụng Helvetica");
        return PDType1Font.HELVETICA;
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