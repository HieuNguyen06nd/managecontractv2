package com.hieunguyen.ManageContract.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${api.upload.dir:uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Chuẩn hóa path
        String root = uploadDir.endsWith("/") ? uploadDir : (uploadDir + "/");

        // Truy cập chung các file trong uploads/*
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + root)
                .setCacheControl(CacheControl.noCache());

        // Cho DS tải file xem trước: /internal/previews/{token}/contract.docx
        registry.addResourceHandler("/internal/previews/**")
                .addResourceLocations("file:" + root + "previews/")
                .setCacheControl(CacheControl.noCache());
    }
}
