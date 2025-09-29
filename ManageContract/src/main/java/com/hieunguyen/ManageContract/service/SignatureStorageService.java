package com.hieunguyen.ManageContract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SignatureStorageService {

    @Value("${app.signature.storage-dir}")
    private String storageDir; // ví dụ: ./uploads/signatures hoặc /var/app/uploads/signatures

    @Value("${app.signature.public-base-url:/static/signatures}")
    private String publicBaseUrl; // ví dụ: /static/signatures

    /**
     * Upload ảnh chữ ký cá nhân (trang hồ sơ). Hỗ trợ PNG/JPEG, lưu thành PNG.
     * Trả về URL public để FE hiển thị.
     */
    public String saveSignature(Long employeeId, MultipartFile file) {
        try {
            if (file.isEmpty()) throw new IllegalArgumentException("Empty file");
            String ct = Optional.ofNullable(file.getContentType()).orElse("").toLowerCase();
            if (!ct.contains("png") && !ct.contains("jpeg") && !ct.contains("jpg")) {
                throw new IllegalArgumentException("Chỉ hỗ trợ PNG/JPEG");
            }

            BufferedImage img = ImageIO.read(file.getInputStream());
            if (img == null) throw new IllegalArgumentException("File không phải ảnh hợp lệ");

            // ./uploads/signatures/{employeeId}/profile-signature-{uuid}.png
            Path dir = Path.of(storageDir, String.valueOf(employeeId));
            Files.createDirectories(dir);

            String filename = "profile-signature-" + UUID.randomUUID() + ".png";
            Path path = dir.resolve(filename);

            try (OutputStream os = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ImageIO.write(img, "png", os);
            }

            return publicBaseUrl + "/" + employeeId + "/" + filename;
        } catch (Exception e) {
            throw new RuntimeException("Lưu ảnh chữ ký thất bại: " + e.getMessage(), e);
        }
    }

    /**
     * Lưu ảnh chữ ký được gửi ở dạng Base64/dataURL khi người dùng ký trong quy trình.
     * Lưu vào thư mục theo hợp đồng + người ký để dễ truy vết:
     */
    public String saveBase64Png(Long contractId, Long employeeId, String base64OrDataUrl) {
        try {
            if (base64OrDataUrl == null || base64OrDataUrl.isBlank()) {
                throw new IllegalArgumentException("Dữ liệu chữ ký rỗng");
            }
            String base64 = base64OrDataUrl;
            int comma = base64.indexOf(',');
            if (comma >= 0) {
                // cắt bỏ header dataURL nếu có
                base64 = base64.substring(comma + 1);
            }

            byte[] bytes = Base64.getDecoder().decode(base64);

            Path dir = Path.of(storageDir, String.valueOf(contractId), String.valueOf(employeeId));
            Files.createDirectories(dir);

            String filename = "sig-" + Instant.now().toEpochMilli() + ".png";
            Path file = dir.resolve(filename);
            Files.write(file, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            return publicBaseUrl + "/" + contractId + "/" + employeeId + "/" + filename;
        } catch (Exception e) {
            throw new RuntimeException("Lưu chữ ký (base64) thất bại: " + e.getMessage(), e);
        }
    }

    // (tuỳ chọn) đọc bytes để trả về qua controller bảo mật
    public byte[] readSignature(Long employeeId, String filename) {
        try {
            Path path = Path.of(storageDir, String.valueOf(employeeId), filename);
            return Files.readAllBytes(path);
        } catch (Exception e) {
            throw new RuntimeException("Không đọc được ảnh chữ ký: " + e.getMessage(), e);
        }
    }
}
