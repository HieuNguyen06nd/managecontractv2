package com.hieunguyen.ManageContract.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class FileUploadService {

    @Value("${app.upload.dir}")
    private String uploadDir; // Đường dẫn thư mục lưu trữ file (có thể cấu hình trong application.properties)

    // Phương thức upload file chung
    public String uploadFile(Long entityId, MultipartFile file, String subDir) throws IOException {
        // Tạo thư mục nếu chưa có
        Path directoryPath = Paths.get(uploadDir, subDir);
        if (!Files.exists(directoryPath)) {
            Files.createDirectories(directoryPath);
        }

        // Lưu file vào thư mục con
        String fileName = entityId + "_" + file.getOriginalFilename();
        Path targetPath = directoryPath.resolve(fileName);

        // Copy file vào vị trí lưu
        Files.copy(file.getInputStream(), targetPath);

        return targetPath.toString(); // Trả về đường dẫn của file đã lưu
    }

    // Phương thức upload file base64 chung
    public String uploadBase64File(Long entityId, String base64File, String subDir) throws IOException {
        // Chuyển base64 thành byte array
        byte[] decodedBytes = java.util.Base64.getDecoder().decode(base64File);

        // Tạo thư mục nếu chưa có
        Path directoryPath = Paths.get(uploadDir, subDir);
        if (!Files.exists(directoryPath)) {
            Files.createDirectories(directoryPath);
        }

        // Lưu file vào thư mục con
        String fileName = entityId + "_signature.png";
        Path targetPath = directoryPath.resolve(fileName);

        // Viết byte array vào file
        Files.write(targetPath, decodedBytes);

        return targetPath.toString(); // Trả về đường dẫn của file đã lưu
    }
}
