package com.hieunguyen.ManageContract.service;

import jakarta.mail.MessagingException;

public interface EmailService {
    void sendVerificationCode(String to, String token);
    void sendOtp(String to, String otp);
    String generateOtp();
    void sendInitialPassword(String email, String tempPassword);

    void sendContractApproved(String to, String contractCode, String approverName, java.time.LocalDateTime approvedAt)
            throws MessagingException;

    void sendContractRejected(String to, String contractCode, String approverName, String reason, java.time.LocalDateTime decidedAt)
            throws MessagingException;
}