package com.hieunguyen.ManageContract.common.constants;

public class SecurityConstants {
    public static final String[] PUBLIC_API = {
            "/api/auth/**",
            "/api/payment/**",
            "/oauth2/**",
            "/api/vnpay/return",
            "api/vnpay/**",
            "/api/**",

            // Thêm các endpoint Swagger/OpenAPI
            "/swagger-ui/**",          // Giao diện Swagger UI
            "/v3/api-docs/**",         // OpenAPI JSON docs
            "/swagger-resources/**",   // Tài nguyên Swagger
            "/webjars/**",             // Thư viện webjars
            "/favicon.ico",             // Biểu tượng
            "/internal/files/**"
    };
}
