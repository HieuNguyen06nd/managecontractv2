package com.hieunguyen.ManageContract.dto.file;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

@Data
@AllArgsConstructor
public class FilePayload {
    private Resource resource;
    private String filename;
    private MediaType mediaType;
    private long contentLength;
}