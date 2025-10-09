package com.hieunguyen.ManageContract.service.impl;

import com.hieunguyen.ManageContract.common.exception.ResourceNotFoundException;
import com.hieunguyen.ManageContract.dto.authAccount.AuthProfileResponse;
import com.hieunguyen.ManageContract.dto.permission.PermissionResponse;
import com.hieunguyen.ManageContract.dto.role.RoleResponse;
import com.hieunguyen.ManageContract.entity.AuthAccount;
import com.hieunguyen.ManageContract.repository.AuthAccountRepository;
import com.hieunguyen.ManageContract.security.jwt.SecurityUtil;
import com.hieunguyen.ManageContract.service.AuthAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthAccountServiceImpl implements AuthAccountService {

    private final AuthAccountRepository authAccountRepository;

    @Override
    public AuthAccount findByEmailOrPhone(String emailOrPhone) {
        return authAccountRepository.findByEmail(emailOrPhone)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Tài khoản không tồn tại với email hoặc số điện thoại: " + emailOrPhone));
    }

    @Override
    public AuthProfileResponse getMyProfile() {
        String email = SecurityUtil.getCurrentUserEmail();
        AuthAccount account = authAccountRepository.findByEmailWithRolesAndPermissions(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản"));
        // Ở trang hồ sơ, nếu bạn muốn bắt buộc phải có employee thì vẫn giữ validate như cũ:
        if (account.getEmployee() == null) {
            throw new ResourceNotFoundException("Không tìm thấy thông tin nhân viên cho tài khoản: " + account.getEmail());
        }
        return convertToAuthProfileResponse(account);
    }

    @Override
    public List<AuthProfileResponse> getAll() {
        // QUAN TRỌNG: lọc bỏ những account không có employee để không ném 404 cho cả danh sách
        List<AuthAccount> accounts = authAccountRepository.findAllWithRolesAndPermissions();
        return accounts.stream()
                .filter(acc -> acc.getEmployee() != null)     // <— thêm dòng này
                .map(this::convertToAuthProfileResponse)
                .toList();
    }

    private AuthProfileResponse convertToAuthProfileResponse(AuthAccount account) {
        var user = account.getEmployee();
        // Ở đây KHÔNG ném lỗi cho getAll() nữa vì đã filter ở trên.
        // (Giữ nguyên phần map role/permission của bạn)
        var roleResponses = account.getUserRoles().stream()
                .map(userRole -> {
                    var role = userRole.getRole();
                    var permissions = role.getRolePermissions().stream()
                            .map(rp -> new PermissionResponse(
                                    rp.getPermission().getId(),
                                    rp.getPermission().getPermissionKey(),
                                    rp.getPermission().getDescription(),
                                    rp.getPermission().getModule()
                            ))
                            .toList();
                    return new RoleResponse(
                            role.getId(),
                            role.getRoleKey(),
                            role.getDescription(),
                            permissions
                    );
                })
                .toList();

        return AuthProfileResponse.builder()
                // GIỮ id = employee.id để FE bind theo id nhân viên (đừng dùng account.id)
                .id(user.getId())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .email(account.getEmail())
                .status(account.getStatus())
                .signatureImage(user.getSignatureImage())
                .avatarImage(user.getAvatarImage())
                .department(user.getDepartment() != null ? user.getDepartment().getName() : null)
                .position(user.getPosition() != null ? user.getPosition().getName() : null)
                .roles(roleResponses)
                .build();
    }
}
