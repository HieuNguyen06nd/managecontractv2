package com.hieunguyen.ManageContract.service;

public interface EmailService {
    void sendVerificationCode(String to, String token);
    void sendOtp(String to, String otp);
    String generateOtp();
    void sendInitialPassword(String email, String tempPassword);
}