package com.hieunguyen.ManageContract.service.impl;

import com.hieunguyen.ManageContract.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    // Lấy URL xác nhận
    @Value("${app.verification.url}")
    private String verificationUrl;

    // Email gửi đi (from)
    @Value("${spring.mail.username}")
    private String fromEmail;

    @Override
    public void sendVerificationCode(String to, String token) {
        try {
            log.info("Chuẩn bị gửi email xác nhận đến: {}", to);
            // Tạo link xác nhận dựa trên URL cấu hình và token
            String verifyLink = verificationUrl + "?token=" + token;
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("Xác nhận đăng ký tài khoản");

            String content = "<p>Nhấn vào link sau để xác nhận email của bạn:</p>"
                    + "<a href=\"" + verifyLink + "\">Xác nhận email</a>";
            helper.setText(content, true);

            mailSender.send(message);
            log.info("Email xác nhận đã được gửi thành công đến: {}", to);
        } catch (MessagingException e) {
            log.error("Lỗi MessagingException khi gửi email xác nhận đến {}: {}", to, e.getMessage());
            throw new RuntimeException("Không thể gửi email xác nhận", e);
        } catch (MailException e) {
            log.error("Lỗi MailException khi gửi email xác nhận đến {}: {}", to, e.getMessage());
            throw new RuntimeException("Lỗi khi gửi email, vui lòng thử lại sau.", e);
        }
    }

    @Override
    public void sendOtp(String to, String otp) {
        try {
            log.info("Chuẩn bị gửi email OTP đến: {}", to);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message);

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("Mã OTP xác thực");
            helper.setText("Mã OTP của bạn là: " + otp + ". Mã này sẽ hết hạn trong 5 phút.");

            mailSender.send(message);
            log.info("Email OTP đã được gửi thành công đến: {}", to);
        } catch (MessagingException e) {
            log.error("Lỗi MessagingException khi gửi email OTP đến {}: {}", to, e.getMessage());
            throw new RuntimeException("Không thể gửi OTP", e);
        } catch (MailException e) {
            log.error("Lỗi MailException khi gửi email OTP đến {}: {}", to, e.getMessage());
            throw new RuntimeException("Lỗi khi gửi OTP, vui lòng thử lại sau.", e);
        }
    }

    @Override
    public String generateOtp() {
        // Tạo OTP gồm 6 chữ số ngẫu nhiên
        int otp = 100000 + new Random().nextInt(900000);
        String otpStr = String.valueOf(otp);
        log.info("OTP được tạo: {}", otpStr);
        return otpStr;
    }
}
