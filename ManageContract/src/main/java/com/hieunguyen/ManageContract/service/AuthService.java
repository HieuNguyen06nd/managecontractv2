package com.hieunguyen.ManageContract.service;

import com.hieunguyen.ManageContract.dto.authAccount.AuthResponse;
import com.hieunguyen.ManageContract.dto.authAccount.RegisterRequest;

public interface AuthService {

    String register(RegisterRequest request);

    boolean verifyEmail(String token);
    void sendOtp(String email);
    AuthResponse login(String identifier, String password, String otp);
    boolean verifyOtp(String email, String otp);
    void logout(String email);
    String refreshToken(String refreshToken);

    void sendForgotPasswordOtp(String email);  // Gửi OTP cho người quên mật khẩu
    void resetPasswordWithOtp(String email, String otp, String newPassword);  // Đặt lại mật khẩu bằng OTP

}
