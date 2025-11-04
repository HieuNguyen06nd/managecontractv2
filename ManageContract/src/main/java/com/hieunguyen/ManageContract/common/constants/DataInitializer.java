package com.hieunguyen.ManageContract.common.constants;

import com.hieunguyen.ManageContract.entity.*;
import com.hieunguyen.ManageContract.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final AuthAccountRepository authAccountRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PasswordEncoder passwordEncoder;

    private record Perm(String key, String desc, String module) {}

    @Override
    @Transactional
    public void run(String... args) {
        // 1) Seed permissions
        int inserted = seedPermissions();

        // 2) Tạo tài khoản admin + role ADMIN + gán tất cả permission
        seedAdminWithAllPermissions();

        log.info("DataInitializer done. Inserted {} permissions (if missing).", inserted);
    }

    private int seedPermissions() {
        // Danh mục quyền đầy đủ
        List<Perm> defs = List.of(
                // CONTRACT
                new Perm("CONTRACT_CREATE",              "Tạo hợp đồng",                                   "CONTRACT"),
                new Perm("CONTRACT_DRAFT_EDIT",          "Sửa nội dung khi ở trạng thái DRAFT",           "CONTRACT"),
                new Perm("CONTRACT_VIEW_OWN",            "Xem hợp đồng do mình tạo/sở hữu",               "CONTRACT"),
                new Perm("CONTRACT_VIEW_DEPT",           "Xem hợp đồng trong phòng ban của mình",         "CONTRACT"),
                new Perm("CONTRACT_VIEW_ALL",            "Xem tất cả hợp đồng",                           "CONTRACT"),
                new Perm("CONTRACT_UPDATE",              "Cập nhật thông tin hợp đồng",                   "CONTRACT"),
                new Perm("CONTRACT_DELETE",              "Xoá hợp đồng",                                  "CONTRACT"),
                new Perm("CONTRACT_SUBMIT_FOR_APPROVAL", "Gửi hợp đồng vào luồng phê duyệt",              "CONTRACT"),
                new Perm("CONTRACT_CANCEL",              "Huỷ yêu cầu/phê duyệt hợp đồng",                "CONTRACT"),
                new Perm("CONTRACT_DUPLICATE",           "Nhân bản hợp đồng",                             "CONTRACT"),
                new Perm("CONTRACT_ARCHIVE",             "Lưu trữ/đóng hợp đồng",                         "CONTRACT"),
                new Perm("CONTRACT_EXPORT_PDF",          "Xuất/PDF hợp đồng",                             "CONTRACT"),
                new Perm("CONTRACT_DOWNLOAD_FILE",       "Tải xuống file hợp đồng",                       "CONTRACT"),
                new Perm("ATTACHMENT_VIEW",              "Xem danh sách tệp đính kèm",                    "CONTRACT"),
                new Perm("ATTACHMENT_UPLOAD",            "Tải lên tệp đính kèm",                          "CONTRACT"),
                new Perm("ATTACHMENT_DELETE",            "Xoá tệp đính kèm",                              "CONTRACT"),
                new Perm("CONTRACT_ASSIGN_OWNER",        "Gán/chuyển chủ sở hữu hợp đồng",                "CONTRACT"),
                new Perm("CONTRACT_CHANGE_FLOW",         "Thay đổi flow phê duyệt cho hợp đồng",          "CONTRACT"),
                new Perm("CONTRACT_VIEW_HISTORY",        "Xem lịch sử/nhật ký hợp đồng",                  "CONTRACT"),

                // APPROVAL
                new Perm("APPROVAL_VIEW_FLOW",           "Xem flow phê duyệt của hợp đồng",               "APPROVAL"),
                new Perm("APPROVAL_STEP_VIEW",           "Xem các bước phê duyệt",                        "APPROVAL"),
                new Perm("APPROVAL_STEP_APPROVE",        "Phê duyệt bước",                                "APPROVAL"),
                new Perm("APPROVAL_STEP_REJECT",         "Từ chối bước",                                  "APPROVAL"),
                new Perm("APPROVAL_STEP_SIGN",           "Ký số ở bước yêu cầu ký",                       "APPROVAL"),
                new Perm("APPROVAL_STEP_SIGN_THEN_APPROVE", "Ký rồi phê duyệt trong một bước",           "APPROVAL"),
                new Perm("APPROVAL_STEP_REASSIGN",       "Chuyển/gán lại người duyệt",                    "APPROVAL"),
                new Perm("APPROVAL_STEP_SKIP",           "Bỏ qua/override một bước (quyền đặc biệt)",    "APPROVAL"),
                new Perm("APPROVAL_ROLLBACK",            "Quay lại bước phê duyệt trước đó",              "APPROVAL"),
                new Perm("APPROVAL_VIEW_QUEUE_OWN",      "Xem hàng chờ phê duyệt của bản thân",           "APPROVAL"),
                new Perm("APPROVAL_VIEW_QUEUE_DEPT",     "Xem hàng chờ phê duyệt của phòng ban",          "APPROVAL"),
                new Perm("APPROVAL_VIEW_QUEUE_ALL",      "Xem toàn bộ hàng chờ phê duyệt",                "APPROVAL"),

                // SIGNATURE
                new Perm("SIGNATURE_UPLOAD_OWN",         "Tải lên/chỉnh sửa chữ ký của bản thân",         "SIGNATURE"),
                new Perm("SIGNATURE_UPLOAD_OTHERS",      "Quản trị tải lên/chỉnh sửa chữ ký người khác",  "SIGNATURE"),
                new Perm("SIGNATURE_DELETE_OWN",         "Xoá chữ ký của bản thân",                       "SIGNATURE"),
                new Perm("SIGNATURE_DELETE_OTHERS",      "Xoá chữ ký của người khác",                     "SIGNATURE"),
                new Perm("SIGNATURE_APPLY_ON_BEHALF",    "Ký thay/đại diện (uỷ quyền)",                   "SIGNATURE"),

                // COMMENT
                new Perm("COMMENT_VIEW",                 "Xem bình luận/ghi chú",                         "COMMENT"),
                new Perm("COMMENT_ADD",                  "Thêm bình luận",                                "COMMENT"),
                new Perm("COMMENT_EDIT_OWN",             "Sửa bình luận của mình",                        "COMMENT"),
                new Perm("COMMENT_DELETE_OWN",           "Xoá bình luận của mình",                        "COMMENT"),
                new Perm("COMMENT_DELETE_ANY",           "Xoá bình luận của người khác",                  "COMMENT"),

                // PARTY
                new Perm("PARTY_VIEW",                   "Xem đối tác/bên tham gia",                      "PARTY"),
                new Perm("PARTY_CREATE",                 "Thêm đối tác/bên tham gia",                     "PARTY"),
                new Perm("PARTY_UPDATE",                 "Sửa đối tác/bên tham gia",                      "PARTY"),
                new Perm("PARTY_DELETE",                 "Xoá đối tác/bên tham gia",                      "PARTY"),

                // TEMPLATE
                new Perm("TEMPLATE_VIEW",                "Xem mẫu hợp đồng",                              "TEMPLATE"),
                new Perm("TEMPLATE_CREATE",              "Tạo mẫu hợp đồng",                              "TEMPLATE"),
                new Perm("TEMPLATE_UPDATE",              "Sửa mẫu hợp đồng",                              "TEMPLATE"),
                new Perm("TEMPLATE_DELETE",              "Xoá mẫu hợp đồng",                              "TEMPLATE"),
                new Perm("TEMPLATE_SET_DEFAULT_FLOW",    "Đặt flow mặc định cho template",                "TEMPLATE"),

                // FLOW
                new Perm("FLOW_VIEW",                    "Xem danh sách flow",                            "FLOW"),
                new Perm("FLOW_CREATE",                  "Tạo flow",                                      "FLOW"),
                new Perm("FLOW_UPDATE",                  "Sửa flow",                                      "FLOW"),
                new Perm("FLOW_DELETE",                  "Xoá flow",                                      "FLOW"),
                new Perm("FLOW_REORDER_STEPS",           "Sắp xếp lại thứ tự bước trong flow",            "FLOW"),

                // ORG (tổ chức)
                new Perm("DEPARTMENT_MANAGE",            "Quản lý phòng ban",                             "ORG"),
                new Perm("POSITION_MANAGE",              "Quản lý vị trí",                                "ORG"),
                new Perm("USER_MANAGE",                  "Quản lý người dùng",                            "ORG"),

                // NOTIFICATION
                new Perm("NOTIFICATION_SEND_MANUAL",     "Gửi thông báo email thủ công",                  "NOTIFICATION"),
                new Perm("NOTIFICATION_CONFIGURE_PREFERENCES", "Cấu hình tuỳ chọn nhận thông báo",       "NOTIFICATION"),
                new Perm("NOTIFICATION_TEMPLATE_MANAGE", "Quản lý mẫu email/thông báo",                   "NOTIFICATION"),

                // REPORT / AUDIT
                new Perm("REPORT_VIEW",                  "Xem báo cáo",                                   "REPORT"),
                new Perm("REPORT_EXPORT",                "Xuất báo cáo",                                  "REPORT"),
                new Perm("AUDIT_VIEW",                   "Xem audit log",                                 "AUDIT"),
                new Perm("AUDIT_EXPORT",                 "Xuất audit log",                                "AUDIT")
        );

        // Bản đồ permission hiện có (upper-case key để so khớp an toàn)
        Map<String, Permission> existing = permissionRepository.findAll()
                .stream()
                .collect(Collectors.toMap(
                        p -> p.getPermissionKey().toUpperCase(Locale.ROOT),
                        p -> p,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        List<Permission> toInsert = new ArrayList<>();
        for (Perm p : defs) {
            String k = p.key().toUpperCase(Locale.ROOT);
            if (!existing.containsKey(k)) {
                Permission perm = Permission.builder()
                        .permissionKey(p.key())
                        .description(p.desc())
                        .module(p.module())
                        .build();
                toInsert.add(perm);
                existing.put(k, perm); // update map để tránh duplicate trong vòng lặp
            }
        }

        if (!toInsert.isEmpty()) {
            permissionRepository.saveAll(toInsert);
            log.info("Seeded {} new permissions.", toInsert.size());
        } else {
            log.info("No new permissions to seed.");
        }
        return toInsert.size();
    }

    private void seedAdminWithAllPermissions() {
        final String defaultEmail = "admin";     // nếu muốn là email thật, đổi thành 'admin@yourdomain.com'
        final String defaultPassword = "123456";

        AuthAccount adminAccount = authAccountRepository.findByEmail(defaultEmail).orElseGet(() -> {
            AuthAccount acc = new AuthAccount();
            acc.setEmail(defaultEmail);
            acc.setEmailVerified(true);
            acc.setPassword(passwordEncoder.encode(defaultPassword));
            acc.setStatus(StatusUser.ACTIVE);
            AuthAccount saved = authAccountRepository.save(acc);
            log.info("Tạo tài khoản admin mặc định: {} / {}", defaultEmail, defaultPassword);
            return saved;
        });

        Role adminRole = roleRepository.findByRoleKey("ADMIN").orElseGet(() -> {
            Role role = new Role();
            role.setRoleKey("ADMIN");
            role.setDescription("Quản trị viên hệ thống");
            Role saved = roleRepository.save(role);
            log.info("Tạo role ADMIN.");
            return saved;
        });

        // Gán role ADMIN cho adminAccount (nếu chưa)
        boolean hasAdminRole = userRoleRepository.existsByAccountAndRole(adminAccount, adminRole);
        if (!hasAdminRole) {
            UserRole ur = new UserRole();
            ur.setAccount(adminAccount);
            ur.setRole(adminRole);
            userRoleRepository.save(ur);
            log.info("Gán role ADMIN cho tài khoản admin.");
        }

        // Gán toàn bộ permission cho ADMIN (an toàn, không trùng)
        List<Permission> allPermissions = permissionRepository.findAll();
        int addCount = 0;
        for (Permission permission : allPermissions) {
            boolean exists = rolePermissionRepository.existsByRoleAndPermission(adminRole, permission);
            if (!exists) {
                RolePermission rp = new RolePermission();
                rp.setRole(adminRole);
                rp.setPermission(permission);
                rolePermissionRepository.save(rp);
                addCount++;
            }
        }
        if (addCount > 0) {
            log.info("Gán thêm {} permission cho ADMIN.", addCount);
        }
    }
}
