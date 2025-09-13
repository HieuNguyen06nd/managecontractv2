package com.hieunguyen.ManageContract.service.impl;

import com.hieunguyen.ManageContract.common.constants.StatusUser;
import com.hieunguyen.ManageContract.dto.authAccount.AuthResponse;
import com.hieunguyen.ManageContract.dto.authAccount.RegisterRequest;
import com.hieunguyen.ManageContract.dto.role.RoleResponse;
import com.hieunguyen.ManageContract.entity.AuthAccount;
import com.hieunguyen.ManageContract.entity.Role;
import com.hieunguyen.ManageContract.entity.User;
import com.hieunguyen.ManageContract.entity.UserRole;
import com.hieunguyen.ManageContract.repository.AuthAccountRepository;
import com.hieunguyen.ManageContract.repository.RoleRepository;
import com.hieunguyen.ManageContract.repository.UserRepository;
import com.hieunguyen.ManageContract.repository.UserRoleRepository;
import com.hieunguyen.ManageContract.security.jwt.JwtUtil;
import com.hieunguyen.ManageContract.service.AuthService;
import com.hieunguyen.ManageContract.service.EmailService;
import com.hieunguyen.ManageContract.service.OtpService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthAccountRepository authAccountRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;
    private final OtpService otpService;
    private final StringRedisTemplate redisTemplate;

    @Override
    @Transactional
    public String register(RegisterRequest request) {
        // 1. Kiểm tra email tồn tại
        if (authAccountRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email đã tồn tại");
        }

        // 2. Lấy danh sách role tương ứng
        List<Role> roles = request.getRoles().stream()
                .map(roleKey -> roleRepository.findByRoleKeyIgnoreCase(roleKey.trim().toUpperCase())
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy vai trò: " + roleKey)))
                .toList();


        // 4. Tạo account
        AuthAccount account = new AuthAccount();
        account.setEmail(request.getEmail());
        account.setPhone(request.getPhone());
        account.setPassword(passwordEncoder.encode(request.getPassword()));
        account.setStatus(StatusUser.PENDING);
        account.setEmailVerified(false);
        account.setEmailVerificationToken(UUID.randomUUID().toString());
        account.setTokenExpiresAt(LocalDateTime.now().plusHours(24));

        authAccountRepository.save(account);

        // 5. Gán các vai trò
        for (Role role : roles) {
            UserRole userRole = new UserRole();
            userRole.setAccount(account);
            userRole.setRole(role);
            userRole.setCreatedAt(LocalDateTime.now());
            userRoleRepository.save(userRole);
        }

        // 5. Tạo User mặc định cho account
        User user = User.builder()
                .account(account)
                .fullName(request.getFullName() != null ? request.getFullName() : "Chưa cập nhật")
                .phone(request.getPhone())
                .build();
        userRepository.save(user);

        // 6. Gửi email xác minh
        emailService.sendVerificationCode(account.getEmail(), account.getEmailVerificationToken());

        return "Đăng ký thành công! Vui lòng kiểm tra email để xác nhận tài khoản.";
    }


    @Override
    public boolean verifyEmail(String token) {
        AuthAccount account = authAccountRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new RuntimeException("Token không hợp lệ"));

        if (account.getTokenExpiresAt() != null && account.getTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Token đã hết hạn");
        }

        account.setEmailVerified(true);
        account.setStatus(StatusUser.ACTIVE);
        account.setEmailVerificationToken(null);
        account.setTokenExpiresAt(null);
        authAccountRepository.save(account);

        return true;
    }


    @Override
    public void sendOtp(String email) {
        AuthAccount account = authAccountRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Email không tồn tại"));

        String otp = otpService.generateOtp(email);
        emailService.sendOtp(email, otp);
    }


    @Override
    public AuthResponse login(String identifier, String password, String otp) {
        AuthAccount account = authAccountRepository.findByEmailWithRolesAndPermissions(identifier)
                .orElseThrow(() -> new RuntimeException("Tài khoản không tồn tại"));

        boolean isAuthenticated = false;

        if (password != null && passwordEncoder.matches(password, account.getPassword())) {
            isAuthenticated = true;
        }

        if (!isAuthenticated && otp != null && otpService.verifyOtp(account.getEmail(), otp)) {
            isAuthenticated = true;
        }

        if (!isAuthenticated) {
            throw new RuntimeException("Đăng nhập thất bại. Cần mật khẩu hoặc OTP.");
        }

        if (!account.isEmailVerified()) {
            throw new RuntimeException("Email chưa được xác minh.");
        }

        List<RoleResponse> roleKeys = account.getUserRoles().stream()
                .map(userRole -> {
                    Role role = userRole.getRole();
                    return new RoleResponse(role.getRoleKey(), role.getDescription());
                })
                .toList();
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", roleKeys);

        String accessToken = jwtUtil.generateTokenWithClaims(account.getEmail(), claims);
        String refreshToken = jwtUtil.generateRefreshToken(account.getEmail());

        redisTemplate.opsForValue().set("TOKEN:" + account.getEmail(), refreshToken);

        return new AuthResponse(accessToken, account.getId(), roleKeys);
    }

    @Override
    public String refreshToken(String refreshToken) {
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new RuntimeException("Refresh token không hợp lệ");
        }

        String email = jwtUtil.extractEmail(refreshToken);
        String storedToken = redisTemplate.opsForValue().get("TOKEN:" + email);

        if (!refreshToken.equals(storedToken)) {
            throw new RuntimeException("Refresh token không hợp lệ hoặc đã hết hạn");
        }

        AuthAccount account = authAccountRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Tài khoản không tồn tại"));

        List<String> roleKeys = account.getUserRoles().stream()
                .map(userRole -> userRole.getRole().getRoleKey())
                .toList();

        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", roleKeys);

        return jwtUtil.generateTokenWithClaims(account.getEmail(), claims);
    }

    @Override
    public boolean verifyOtp(String email, String otp) {
        return otpService.verifyOtp(email, otp);
    }

    @Override
    public void logout(String email) {
        Set<String> keys = redisTemplate.keys("TOKEN:" + email + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Override
    public void sendForgotPasswordOtp(String email) {
        AuthAccount account = authAccountRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Email không tồn tại"));

        String otp = otpService.generateOtp(email);
        emailService.sendOtp(email, otp);
    }

    @Override
    public void resetPasswordWithOtp(String email, String otp, String newPassword) {
        AuthAccount account = authAccountRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Email không tồn tại"));

        if (!otpService.verifyOtp(email, otp)) {
            throw new RuntimeException("OTP không hợp lệ hoặc đã hết hạn");
        }

        account.setPassword(passwordEncoder.encode(newPassword));
        authAccountRepository.save(account);

        // Xoá OTP sau khi dùng
        otpService.clearOtp(email);
    }
}
