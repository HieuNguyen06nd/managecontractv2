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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.verification.url}")
    private String verificationUrl;

    @Value("${spring.mail.username}")
    private String fromEmail;

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // ========= Public API =========

    @Override
    public void sendVerificationCode(String to, String token) {
        try {
            log.info("Chuẩn bị gửi email xác nhận đến: {}", to);
            String verifyLink = verificationUrl + "?token=" + token;
            String subject = "Xác nhận đăng ký tài khoản";
            String html = "<p>Nhấn vào link sau để xác nhận email của bạn:</p>"
                    + "<a href=\"" + verifyLink + "\">Xác nhận email</a>";
            sendHtml(to, subject, html);
            log.info("Email xác nhận đã được gửi thành công đến: {}", to);
        } catch (RuntimeException e) {
            log.error("Lỗi khi gửi email xác nhận đến {}: {}", to, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void sendOtp(String to, String otp) {
        try {
            log.info("Chuẩn bị gửi email OTP đến: {}", to);
            String subject = "Mã OTP xác thực";
            String text = "Mã OTP của bạn là: " + otp + ". Mã này sẽ hết hạn trong 5 phút.";
            sendText(to, subject, text);
            log.info("Email OTP đã được gửi thành công đến: {}", to);
        } catch (RuntimeException e) {
            log.error("Lỗi khi gửi email OTP đến {}: {}", to, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public String generateOtp() {
        int otp = 100000 + new Random().nextInt(900000);
        String otpStr = String.valueOf(otp);
        log.info("OTP được tạo: {}", otpStr);
        return otpStr;
    }

    @Override
    public void sendInitialPassword(String email, String tempPassword) {
        String subject = "Mật khẩu tạm thời";
        String html = """
            <p>Chào bạn,</p>
            <p>Tài khoản của bạn đã được tạo.</p>
            <p><b>Mật khẩu tạm:</b> %s</p>
            <p>Vui lòng đăng nhập và <b>đổi mật khẩu</b> để kích hoạt.</p>
        """.formatted(tempPassword);
        sendHtml(email, subject, html);
    }

    // ====== Các hàm thông báo hợp đồng (dùng lại cho listener) ======

    @Override
    public void sendContractApproved(String to, String contractCode, String approverName, LocalDateTime approvedAt) {
        String subject = "[Hợp đồng " + (contractCode != null ? contractCode : "") + "] đã được phê duyệt";
        String timeStr = approvedAt != null ? approvedAt.format(TS) : "";
        String html = """
            <p>Xin chào,</p>
            <p>Hợp đồng <b>%s</b> đã được <b>PHÊ DUYỆT</b> lúc <b>%s</b> bởi <b>%s</b>.</p>
            <p>Trân trọng.</p>
        """.formatted(n2e(contractCode), timeStr, n2e(approverName));
        sendHtml(to, subject, html);
    }

    @Override
    public void sendContractRejected(String to, String contractCode, String approverName, String reason, LocalDateTime decidedAt) {
        String subject = "[Hợp đồng " + (contractCode != null ? contractCode : "") + "] BỊ TỪ CHỐI";
        String timeStr = decidedAt != null ? decidedAt.format(TS) : "";
        String html = """
            <p>Xin chào,</p>
            <p>Hợp đồng <b>%s</b> đã bị <b>TỪ CHỐI</b> lúc <b>%s</b> bởi <b>%s</b>.</p>
            <p><b>Lý do:</b> %s</p>
            <p>Trân trọng.</p>
        """.formatted(n2e(contractCode), timeStr, n2e(approverName), n2e(reason));
        sendHtml(to, subject, html);
    }

    // ========= Private helpers (DUY NHẤT 1 sendHtml) =========

    private void sendHtml(String to, String subject, String html) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(mime);
        } catch (MessagingException e) {
            throw new RuntimeException("Gửi email thất bại", e);
        } catch (MailException e) {
            throw new RuntimeException("Lỗi khi gửi email, vui lòng thử lại sau.", e);
        }
    }

    private void sendText(String to, String subject, String text) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, false, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, false);
            mailSender.send(mime);
        } catch (MessagingException e) {
            throw new RuntimeException("Gửi email thất bại", e);
        } catch (MailException e) {
            throw new RuntimeException("Lỗi khi gửi email, vui lòng thử lại sau.", e);
        }
    }

    private static String n2e(String s) { return s == null ? "" : s; }
}
