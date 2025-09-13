package com.hieunguyen.ManageContract.controller;

import com.hieunguyen.ManageContract.dto.ResponseData;
import com.hieunguyen.ManageContract.dto.authAccount.AuthRequest;
import com.hieunguyen.ManageContract.dto.authAccount.AuthResponse;
import com.hieunguyen.ManageContract.dto.authAccount.RegisterRequest;
import com.hieunguyen.ManageContract.dto.authAccount.ResetPasswordRequest;
import com.hieunguyen.ManageContract.service.AuthAccountService;
import com.hieunguyen.ManageContract.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthAccountService authAccountService;

    // Đăng ký
    @PostMapping("/register")
    @Operation(summary = "Dăng ký tài khoản người dùng")
    public ResponseData<String> register(@Valid @RequestBody RegisterRequest request) {
        String message = authService.register(request);
        return new ResponseData<>(200, "Đăng ký thành công", message);
    }

    // Xác thực email
    @GetMapping("/verify-email")
    @Operation(summary = "Xác thực email tài khoản người dùng")
    public ResponseData<String> verifyEmail(@RequestParam("token") String token) {
        boolean verified = authService.verifyEmail(token);
        return new ResponseData<>(200, "Xác thực email thành công", "OK");
    }

    // Gửi OTP
    @PostMapping("/send-otp")
    @Operation(summary = "Gửi OTP xác thực")
    public ResponseData<String> sendOtp(@RequestParam("email") String email) {
        authService.sendOtp(email);
        return new ResponseData<>(200, "OTP đã được gửi đến email: " + email, null);
    }

    // Đăng nhập
    @PostMapping("/login")
    @Operation(summary = "Dăng nhập tài khoản người dùng")
    public ResponseData<AuthResponse> login(@RequestBody AuthRequest request) {
        // Gọi service để xử lý login, nhận về đối tượng AuthResponse
        AuthResponse response = authService.login(request.getEmailOrPhone(), request.getPassword(), request.getOtp());

        return new ResponseData<>(200, "Đăng nhập thành công", response);
    }

    // Xác thực OTP
    @PostMapping("/verify-otp")
    @Operation(summary = "Xác minh OTP email")
    public ResponseData<String> verifyOtp(@RequestParam("email") String email,
                                          @RequestParam("otp") String otp) {
        boolean valid = authService.verifyOtp(email, otp);
        return new ResponseData<>(200, "Xác thực OTP thành công", "OK");
    }

    // Đăng xuất
    @PostMapping("/logout")
    @Operation(summary = "Dăng xuất tài khoản")
    public ResponseData<String> logout(@RequestParam String email) {
        authService.logout(email);
        return new ResponseData<>(200, "Đăng xuất thành công", "OK");
    }

    @PostMapping("/forgot-password/send-otp")
    @Operation(summary = "Quên mật khẩu tài khoản")
    public ResponseData<String> sendForgotPasswordOtp(@RequestParam String email) {
        authService.sendForgotPasswordOtp(email);
        return new ResponseData<>(200, "Gửi OTP thành công" + email);
    }

    @PostMapping("/forgot-password/reset")
    @Operation(summary = "Reset password")
    public ResponseData<String> resetPassword(@RequestBody ResetPasswordRequest request) {
        authService.resetPasswordWithOtp(request.getEmail(), request.getOtp(), request.getNewPassword());
        return new ResponseData<>(200, "Đặt lại mật khẩu thành công");
    }
}
