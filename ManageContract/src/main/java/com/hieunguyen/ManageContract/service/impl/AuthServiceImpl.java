package com.hieunguyen.ManageContract.service.impl;

import com.hieunguyen.ManageContract.common.constants.StatusUser;
import com.hieunguyen.ManageContract.dto.authAccount.AdminCreateUserRequest;
import com.hieunguyen.ManageContract.dto.authAccount.AuthResponse;
import com.hieunguyen.ManageContract.dto.authAccount.RegisterRequest;
import com.hieunguyen.ManageContract.dto.authAccount.ResetPasswordRequest;
import com.hieunguyen.ManageContract.dto.permission.PermissionResponse;
import com.hieunguyen.ManageContract.dto.role.RoleResponse;
import com.hieunguyen.ManageContract.entity.*;
import com.hieunguyen.ManageContract.repository.*;
import com.hieunguyen.ManageContract.security.jwt.JwtUtil;
import com.hieunguyen.ManageContract.service.AuthService;
import com.hieunguyen.ManageContract.service.EmailService;
import com.hieunguyen.ManageContract.service.OtpService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
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
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;

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
        Employee employee = Employee.builder()
                .account(account)
                .fullName(request.getFullName() != null ? request.getFullName() : "Chưa cập nhật")
                .phone(request.getPhone())
                .build();
        userRepository.save(employee);

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

        // password
        if (password != null && passwordEncoder.matches(password, account.getPassword())) {
            isAuthenticated = true;
        }
        // otp
        if (!isAuthenticated && otp != null && otpService.verifyOtp(account.getEmail(), otp)) {
            isAuthenticated = true;
        }
        if (!isAuthenticated) {
            throw new RuntimeException("Đăng nhập thất bại. Cần mật khẩu hoặc OTP.");
        }

        //  nếu cần đổi mật khẩu lần đầu → trả token đổi mật khẩu, KHÔNG chặn bởi emailVerified
        if (Boolean.TRUE.equals(account.getMustChangePassword())) {
            Map<String, Object> claimsCp = new HashMap<>();
            claimsCp.put("scope", "change_password");
            claimsCp.put("email", account.getEmail());
            // token sống 10 phút
            String changePwdToken = jwtUtil.generateTokenWithClaims(account.getEmail(), claimsCp, 10 * 60);
            return new AuthResponse(null, null, account.getId(), List.of(), true, changePwdToken);
        }

        // Đến đây là user bình thường: yêu cầu đã verified + ACTIVE
        if (!account.isEmailVerified()) {
            throw new RuntimeException("Tài khoản chưa được kích hoạt. Vui lòng đổi mật khẩu để kích hoạt.");
        }
        if (account.getStatus() != StatusUser.ACTIVE) {
            throw new RuntimeException("Tài khoản chưa ở trạng thái ACTIVE.");
        }

        // map role + permissions như cũ
        List<RoleResponse> roleResponses = account.getUserRoles().stream()
                .map(userRole -> {
                    Role role = userRole.getRole();
                    List<PermissionResponse> permissionResponses = role.getRolePermissions().stream()
                            .map(rp -> new PermissionResponse(
                                    rp.getPermission().getId(),
                                    rp.getPermission().getPermissionKey(),
                                    rp.getPermission().getDescription(),
                                    rp.getPermission().getModule()
                            )).toList();
                    return new RoleResponse(role.getId(), role.getRoleKey(), role.getDescription(), permissionResponses);
                }).toList();

        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", roleResponses);

        String accessToken = jwtUtil.generateTokenWithClaims(account.getEmail(), claims);
        String refreshToken = jwtUtil.generateRefreshToken(account.getEmail());
        redisTemplate.opsForValue().set("TOKEN:" + account.getEmail(), refreshToken);

        return AuthResponse.normal(accessToken, refreshToken, account.getId(), roleResponses);
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
    public void resetPasswordWithOtp(ResetPasswordRequest request) {
        AuthAccount account = authAccountRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Email không tồn tại"));

        account.setPassword(passwordEncoder.encode(request.getNewPassword()));
        authAccountRepository.save(account);
    }

    @Transactional
    @Override
    public String createUserByAdmin(AdminCreateUserRequest req) {
        if (authAccountRepository.existsByEmail(req.getEmail()))
            throw new RuntimeException("Email đã tồn tại");

        // roles (mặc định EMPLOYEE)
        List<String> keys = (req.getRoleKeys()==null || req.getRoleKeys().isEmpty())
                ? List.of("EMPLOYEE") : req.getRoleKeys();

        List<Role> roles = new ArrayList<>();
        for (String k : keys) {
            roles.add(roleRepository.findByRoleKeyIgnoreCase(k.trim().toUpperCase())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy vai trò: " + k)));
        }

        Department dep = (req.getDepartmentId()==null) ? null :
                departmentRepository.findById(req.getDepartmentId())
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng ban"));

        Position pos = (req.getPositionId()==null) ? null :
                positionRepository.findById(req.getPositionId())
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy chức danh"));

        // mật khẩu tạm
        String tempPassword = generateTempPassword(12);

        // account ở trạng thái chờ kích hoạt
        AuthAccount acc = new AuthAccount();
        acc.setEmail(req.getEmail());
        acc.setPhone(req.getPhone());
        acc.setPassword(passwordEncoder.encode(tempPassword));
        acc.setStatus(StatusUser.PENDING);
        acc.setEmailVerified(false);
        acc.setMustChangePassword(true);
        acc.setPasswordIssuedAt(LocalDateTime.now());
        acc.setEmailVerificationToken(null);
        acc.setTokenExpiresAt(null);
        authAccountRepository.save(acc);

        for (Role r : roles) {
            UserRole ur = new UserRole();
            ur.setAccount(acc); ur.setRole(r);
            ur.setCreatedAt(LocalDateTime.now());
            userRoleRepository.save(ur);
        }

        Employee emp = Employee.builder()
                .account(acc)
                .fullName(Objects.requireNonNullElse(req.getFullName(),"Chưa cập nhật"))
                .phone(req.getPhone())
                .department(dep)
                .position(pos)
                .build();
        userRepository.save(emp);

        emailService.sendInitialPassword(acc.getEmail(), tempPassword);
        return "Tạo tài khoản thành công. Mật khẩu tạm đã gửi email người dùng.";
    }

    private String generateTempPassword(int length) {
        if (length < 8) length = 8;
        final String U = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        final String L = "abcdefghijkmnpqrstuvwxyz";
        final String D = "23456789";
        final String S = "@#$%*&!?";
        String all = U+L+D+S;
        SecureRandom r = new SecureRandom();
        List<Character> p = new ArrayList<>();
        p.add(U.charAt(r.nextInt(U.length())));
        p.add(L.charAt(r.nextInt(L.length())));
        p.add(D.charAt(r.nextInt(D.length())));
        p.add(S.charAt(r.nextInt(S.length())));
        for (int i=p.size(); i<length; i++) p.add(all.charAt(r.nextInt(all.length())));
        Collections.shuffle(p, r);
        StringBuilder sb = new StringBuilder();
        p.forEach(sb::append);
        return sb.toString();
    }

    @Transactional
    @Override
    public AuthResponse changePasswordFirstLogin(String changePasswordJwt, String newPassword) {
        if (changePasswordJwt == null || !jwtUtil.validateToken(changePasswordJwt)) {
            throw new RuntimeException("Token đổi mật khẩu không hợp lệ");
        }

        // Lấy scope từ claim
        String scope = jwtUtil.extractClaim(changePasswordJwt, c -> c.get("scope", String.class));
        if (!"change_password".equals(scope)) {
            throw new RuntimeException("Token không có quyền đổi mật khẩu lần đầu");
        }

        // Lấy email (subject) từ token
        String email = jwtUtil.extractEmail(changePasswordJwt);

        AuthAccount acc = authAccountRepository.findByEmailWithRolesAndPermissions(email)
                .orElseThrow(() -> new RuntimeException("Tài khoản không tồn tại"));

        if (!Boolean.TRUE.equals(acc.getMustChangePassword())) {
            throw new RuntimeException("Tài khoản đã được kích hoạt trước đó.");
        }

        // Đổi mật khẩu + kích hoạt
        acc.setPassword(passwordEncoder.encode(newPassword));
        acc.setMustChangePassword(false);
        acc.setEmailVerified(true);
        acc.setStatus(StatusUser.ACTIVE);
        acc.setEmailVerificationToken(null);
        acc.setTokenExpiresAt(null);
        authAccountRepository.save(acc);

        // Map role -> claims + phát access/refresh như bạn đang làm
        List<RoleResponse> roleResponses = acc.getUserRoles().stream().map(userRole -> {
            Role role = userRole.getRole();
            List<PermissionResponse> permissionResponses = role.getRolePermissions().stream()
                    .map(rp -> new PermissionResponse(
                            rp.getPermission().getId(),
                            rp.getPermission().getPermissionKey(),
                            rp.getPermission().getDescription(),
                            rp.getPermission().getModule()
                    )).toList();
            return new RoleResponse(role.getId(), role.getRoleKey(), role.getDescription(), permissionResponses);
        }).toList();

        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", roleResponses);

        String accessToken = jwtUtil.generateTokenWithClaims(email, claims);
        String refreshToken = jwtUtil.generateRefreshToken(email);
        redisTemplate.opsForValue().set("TOKEN:" + email, refreshToken);

        // nếu bạn dùng record AuthResponse mới:
        return new AuthResponse(accessToken, refreshToken, acc.getId(), roleResponses, false, null);
    }


}
