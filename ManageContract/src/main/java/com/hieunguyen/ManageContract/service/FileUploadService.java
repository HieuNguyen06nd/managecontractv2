package com.hieunguyen.ManageContract.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

@Service
public class FileUploadService {

    @Value("${api.upload.dir}")
    private String uploadDir; // Đường dẫn thư mục lưu trữ file (có thể cấu hình trong application.properties)

    /** Lưu file multipart, trả về path tương đối: subDir/filename */
    public String saveMultipart(Long ownerId, MultipartFile file, String subDir) throws Exception {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("File rỗng");

        String ext = getExt(file.getOriginalFilename());
        String safeName = timeStamped(ownerId, ext);
        Path dir = Paths.get(uploadDir, subDir).normalize();
        Files.createDirectories(dir);

        Path target = dir.resolve(safeName);
        Files.copy(file.getInputStream(), target, REPLACE_EXISTING);

        return Paths.get(subDir, safeName).toString().replace('\\','/');
    }

    /** Lưu data URL hoặc chuỗi base64, trả về path tương đối: subDir/filename */
    public String saveBase64(Long ownerId, String dataUrlOrBase64, String subDir, String preferExt) throws Exception {
        if (dataUrlOrBase64 == null || dataUrlOrBase64.isBlank()) throw new IllegalArgumentException("Base64 rỗng");

        String base64 = dataUrlOrBase64;
        String ext = preferExt;
        // data:image/png;base64,xxxx
        int comma = dataUrlOrBase64.indexOf(',');
        if (dataUrlOrBase64.startsWith("data:") && comma > 0) {
            String meta = dataUrlOrBase64.substring(5, comma); // image/png;base64
            if (meta.contains("image/png"))  ext = "png";
            else if (meta.contains("image/jpeg") || meta.contains("image/jpg")) ext = "jpg";
            base64 = dataUrlOrBase64.substring(comma + 1);
        }
        if (ext == null || ext.isBlank()) ext = "png";

        byte[] bytes = Base64.getDecoder().decode(base64);

        String safeName = timeStamped(ownerId, "." + ext.replace(".", ""));
        Path dir = Paths.get(uploadDir, subDir).normalize();
        Files.createDirectories(dir);

        Path target = dir.resolve(safeName);
        Files.write(target, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        return Paths.get(subDir, safeName).toString().replace('\\','/');
    }

    private String getExt(String original) {
        if (original == null) return ".bin";
        int dot = original.lastIndexOf('.');
        return dot >= 0 ? original.substring(dot) : ".bin";
    }

    private String timeStamped(Long ownerId, String extOrWithDot) {
        String ext = extOrWithDot.startsWith(".") ? extOrWithDot : "." + extOrWithDot;
        String ts = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
        return ownerId + "_" + ts + ext.toLowerCase();
    }
}
