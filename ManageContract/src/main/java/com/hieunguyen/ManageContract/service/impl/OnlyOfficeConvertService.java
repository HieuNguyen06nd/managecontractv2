package com.hieunguyen.ManageContract.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Service;
import org.apache.http.entity.mime.MultipartEntityBuilder;


import java.nio.file.Files;
import java.nio.file.Path;

@Service
@Slf4j
public class OnlyOfficeConvertService {

    private final String onlyOfficeServerUrl = "http://localhost:8080";

    /**
     * Convert DOCX to PDF using OnlyOffice - Nhanh và giữ nguyên định dạng
     */
    public boolean convertDocxToPdf(Path docxPath, Path pdfPath) {
        if (!Files.exists(docxPath)) {
            log.error("DOCX file not found: {}", docxPath);
            return false;
        }

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            // Tạo multipart request
            HttpPost httpPost = new HttpPost(onlyOfficeServerUrl + "/ConvertService.ashx");

            // Tạo multipart entity với HttpClient 4.x
            HttpEntity entity = MultipartEntityBuilder.create()
                    .addBinaryBody("file", docxPath.toFile())
                    .addTextBody("outputtype", "pdf")
                    .build();

            httpPost.setEntity(entity);

            log.info("Sending conversion request to OnlyOffice: {} -> {}", docxPath, pdfPath);

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                if (response.getStatusLine().getStatusCode() == 200) {
                    byte[] pdfBytes = EntityUtils.toByteArray(response.getEntity());
                    Files.write(pdfPath, pdfBytes);
                    log.info("✅ SUCCESS: Converted DOCX to PDF using OnlyOffice: {}", pdfPath);
                    return true;
                } else {
                    log.error("❌ OnlyOffice conversion failed with status: {}", response.getStatusLine().getStatusCode());
                    return false;
                }
            }
        } catch (Exception e) {
            log.error("❌ OnlyOffice conversion error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Kiểm tra OnlyOffice server có hoạt động không
     */
    public boolean isOnlyOfficeAvailable() {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(onlyOfficeServerUrl + "/ConvertService.ashx");

            HttpEntity entity = MultipartEntityBuilder.create()
                    .addTextBody("outputtype", "pdf")
                    .build();

            httpPost.setEntity(entity);

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                // Even if it returns error, it means server is running
                return true;
            }
        } catch (Exception e) {
            log.warn("OnlyOffice server not available: {}", e.getMessage());
            return false;
        }
    }
}