package com.hieunguyen.ManageContract.common.constants;

import com.hieunguyen.ManageContract.entity.*;
import com.hieunguyen.ManageContract.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final AuthAccountRepository authAccountRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        String defaultEmail = "admin";
        String defaultPassword = "123456";

        if (authAccountRepository.findByEmail(defaultEmail).isEmpty()) {
            // Tạo tài khoản admin
            AuthAccount adminAccount = new AuthAccount();
            adminAccount.setEmail(defaultEmail);
            adminAccount.setEmail(defaultEmail);
            adminAccount.setEmailVerified(true);
            adminAccount.setPassword(passwordEncoder.encode(defaultPassword));
            adminAccount.setStatus(StatusUser.ACTIVE);
            adminAccount = authAccountRepository.save(adminAccount);

            // Tạo role admin nếu chưa có
            Role adminRole = roleRepository.findByRoleKey("ADMIN").orElseGet(() -> {
                Role role = new Role();
                role.setRoleKey("ADMIN");
                role.setDescription("Quản trị viên hệ thống");
                return roleRepository.save(role);
            });

            // Gán role admin cho user
            UserRole userRole = new UserRole();
            userRole.setAccount(adminAccount);
            userRole.setRole(adminRole);
            userRoleRepository.save(userRole);

            // Gán tất cả permission hiện có cho role admin
            List<Permission> allPermissions = permissionRepository.findAll();
            for (Permission permission : allPermissions) {
                boolean exists = rolePermissionRepository.existsByRoleAndPermission(adminRole, permission);
                if (!exists) {
                    RolePermission rp = new RolePermission();
                    rp.setRole(adminRole);
                    rp.setPermission(permission);
                    rolePermissionRepository.save(rp);
                }
            }

            System.out.println(" Tài khoản admin đã được khởi tạo: admin / 123456");
        } else {
            System.out.println(" Tài khoản admin đã tồn tại. Bỏ qua khởi tạo.");
        }
    }
}