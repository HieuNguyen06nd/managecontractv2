package com.hieunguyen.ManageContract.dto.file;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

/**
 * Gói dữ liệu file để controller trả về gọn gàng.
 * Yêu cầu Java 16+ (record).
 */
public record FilePayload(
        Resource resource,
        String filename,
        MediaType mediaType,
        long length
) {}
