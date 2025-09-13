package com.hieunguyen.ManageContract.dto.authAccount;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AuthRequest {
    @NotBlank(message = "Email hoặc số điện thoại không được để trống")
    private String emailOrPhone;

    @NotBlank(message = "Mật khẩu không được để trống")
    private String password;

    private String otp;
}