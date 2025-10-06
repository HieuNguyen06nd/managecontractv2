package com.hieunguyen.ManageContract.service;

import com.hieunguyen.ManageContract.dto.authAccount.AdminCreateUserRequest;
import com.hieunguyen.ManageContract.dto.authAccount.AuthResponse;
import com.hieunguyen.ManageContract.dto.authAccount.RegisterRequest;
import com.hieunguyen.ManageContract.dto.authAccount.ResetPasswordRequest;

public interface AuthService {

    String register(RegisterRequest request);

    boolean verifyEmail(String token);
    void sendOtp(String email);
    AuthResponse login(String identifier, String password, String otp);
    boolean verifyOtp(String email, String otp);
    void logout(String email);
    String refreshToken(String refreshToken);

    void sendForgotPasswordOtp(String email);  // Gửi OTP cho người quên mật khẩu
    void resetPasswordWithOtp(ResetPasswordRequest request);  // Đặt lại mật khẩu bằng OTP
    String createUserByAdmin(AdminCreateUserRequest req);
    AuthResponse changePasswordFirstLogin(String changePasswordJwt, String newPassword);

}
