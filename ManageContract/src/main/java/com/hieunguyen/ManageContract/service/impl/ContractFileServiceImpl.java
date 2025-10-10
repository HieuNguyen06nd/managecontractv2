package com.hieunguyen.ManageContract.service.impl;

import com.hieunguyen.ManageContract.entity.Contract;
import com.hieunguyen.ManageContract.repository.ContractRepository;
import com.hieunguyen.ManageContract.service.ContractFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Base64;

@Service
@Slf4j
@RequiredArgsConstructor
public class ContractFileServiceImpl implements ContractFileService {

    private final ContractRepository contractRepository;

    @Value("${app.signature.storage-dir:uploads/signatures}")
    private String signatureStorageDir;

    @Override
    public String generateContractFile(Contract contract) {
        try {
            // Tạo thư mục lưu contract
            Path contractDir = Paths.get("uploads", "contracts", String.valueOf(contract.getId()));
            Files.createDirectories(contractDir);

            Path pdfPath = contractDir.resolve("contract.pdf");

            // Tạo file PDF mẫu
            createSamplePdf(pdfPath, contract);

            return pdfPath.toString();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi generate contract file", e);
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
                // Lấy bytes ảnh từ URL hoặc base64
                byte[] imageBytes = loadImageBytes(imageUrl);
                if (imageBytes == null) {
                    throw new RuntimeException("Cannot load signature image");
                }

                PDImageXObject image = PDImageXObject.createFromByteArray(document, imageBytes, "signature");

                // Tìm placeholder và thay thế bằng ảnh
                boolean replaced = replacePlaceholderWithImage(document, placeholder, image);

                if (!replaced) {
                    log.warn("Placeholder {} not found in PDF, adding to first page", placeholder);
                    // Nếu không tìm thấy placeholder, thêm vào trang đầu
                    addImageToFirstPage(document, image);
                }

                document.save(tempPdf.toFile());
            }

            // Thay thế file gốc
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
                // Thêm text phê duyệt vào trang cuối
                PDPage lastPage = document.getPage(document.getNumberOfPages() - 1);
                addTextToPage(document, lastPage, approvalText, 50, 50); // Vị trí góc trái dưới

                document.save(tempPdf.toFile());
            }

            // Thay thế file gốc
            Files.move(tempPdf, pdfPath, StandardCopyOption.REPLACE_EXISTING);

        } catch (Exception e) {
            throw new RuntimeException("Add approval text failed: " + e.getMessage(), e);
        }
    }

    private byte[] loadImageBytes(String imageUrl) {
        try {
            if (imageUrl.startsWith("data:")) {
                // Xử lý base64
                String base64Data = imageUrl.substring(imageUrl.indexOf(",") + 1);
                return Base64.getDecoder().decode(base64Data);
            } else if (imageUrl.startsWith("http")) {
                // Tải từ URL - đơn giản hóa, giả sử là URL trực tiếp
                // Trong thực tế cần dùng HTTP client
                Path imagePath = Paths.get(signatureStorageDir, extractFilenameFromUrl(imageUrl));
                if (Files.exists(imagePath)) {
                    return Files.readAllBytes(imagePath);
                }
                return null;
            } else {
                // Đọc từ file system
                Path imagePath = Paths.get(imageUrl);
                if (Files.exists(imagePath)) {
                    return Files.readAllBytes(imagePath);
                }
                // Thử trong thư mục signature storage
                imagePath = Paths.get(signatureStorageDir, imageUrl);
                if (Files.exists(imagePath)) {
                    return Files.readAllBytes(imagePath);
                }
            }
        } catch (Exception e) {
            log.error("Failed to load image: {}", e.getMessage());
        }
        return null;
    }

    private String extractFilenameFromUrl(String url) {
        // Đơn giản: lấy phần sau cùng của URL
        return url.substring(url.lastIndexOf("/") + 1);
    }

    private boolean replacePlaceholderWithImage(PDDocument document, String placeholder, PDImageXObject image) {
        try {
            // Đơn giản: thêm vào trang đầu với vị trí cố định dựa trên placeholder
            PDPage page = document.getPage(0);

            // Xác định vị trí dựa trên placeholder
            float x = 100f, y = 100f;
            float width = 120f, height = 60f;

            if (placeholder.contains("MANAGER")) {
                x = 400f; y = 200f;
            } else if (placeholder.contains("DIRECTOR")) {
                x = 400f; y = 150f;
            } else if (placeholder.contains("PARTY_A")) {
                x = 100f; y = 200f;
            } else if (placeholder.contains("PARTY_B")) {
                x = 100f; y = 150f;
            }

            addImageToPage(document, page, image, x, y, width, height);
            return true;
        } catch (Exception e) {
            log.error("Error replacing placeholder with image: {}", e.getMessage());
            return false;
        }
    }

    private void addImageToFirstPage(PDDocument document, PDImageXObject image) {
        try {
            PDPage page = document.getPage(0);
            addImageToPage(document, page, image, 100, 100, 120, 60);
        } catch (Exception e) {
            log.error("Error adding image to first page: {}", e.getMessage());
        }
    }

    // PHƯƠNG THỨC ĐÃ SỬA - THÊM THAM SỐ document
    private void addImageToPage(PDDocument document, PDPage page, PDImageXObject image, float x, float y, float width, float height) {
        try (PDPageContentStream contentStream = new PDPageContentStream(
                document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
            contentStream.drawImage(image, x, y, width, height);
        } catch (IOException e) {
            log.error("Error drawing image on page: {}", e.getMessage());
        }
    }

    // PHƯƠNG THỨC ĐÃ SỬA - THÊM THAM SỐ document
    private void addTextToPage(PDDocument document, PDPage page, String text, float x, float y) {
        try (PDPageContentStream contentStream = new PDPageContentStream(
                document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
            contentStream.beginText();
            contentStream.setFont(PDType1Font.HELVETICA_BOLD, 10);
            contentStream.newLineAtOffset(x, y);
            contentStream.showText(text);
            contentStream.endText();
        } catch (IOException e) {
            log.error("Error adding text to page: {}", e.getMessage());
        }
    }

    private void createSamplePdf(Path pdfPath, Contract contract) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 16);
                contentStream.newLineAtOffset(100, 750);
                contentStream.showText("HỢP ĐỒNG");
                contentStream.endText();

                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 12);
                contentStream.newLineAtOffset(100, 700);
                contentStream.showText("Tiêu đề: " + (contract.getTitle() != null ? contract.getTitle() : "Không có tiêu đề"));
                contentStream.endText();

                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA, 10);
                contentStream.newLineAtOffset(100, 650);
                contentStream.showText("Số hợp đồng: " + (contract.getContractNumber() != null ? contract.getContractNumber() : "N/A"));
                contentStream.endText();

                // Thêm các placeholder cho chữ ký
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA, 10);
                contentStream.newLineAtOffset(100, 300);
                contentStream.showText("Chữ ký bên A: {{SIGN_PARTY_A}}");
                contentStream.newLineAtOffset(0, -30);
                contentStream.showText("Chữ ký bên B: {{SIGN_PARTY_B}}");
                contentStream.newLineAtOffset(0, -30);
                contentStream.showText("Chữ ký quản lý: {{SIGN_MANAGER}}");
                contentStream.newLineAtOffset(0, -30);
                contentStream.showText("Chữ ký giám đốc: {{SIGN_DIRECTOR}}");
                contentStream.endText();

                // Thêm phần chờ phê duyệt ở cuối
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA, 8);
                contentStream.newLineAtOffset(100, 50);
                contentStream.showText("Khu vực hiển thị thông tin phê duyệt (Tên + SĐT)");
                contentStream.endText();
            }

            document.save(pdfPath.toFile());
            log.info("Created sample PDF at: {}", pdfPath);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create sample PDF", e);
        }
    }
}