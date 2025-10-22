package com.hieunguyen.ManageContract.controller;

import com.hieunguyen.ManageContract.dto.ResponseData;
import com.hieunguyen.ManageContract.service.RolePermissionService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/role-permissions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class RolePermissionController {

    private final RolePermissionService rolePermissionService;

    // Gán mới quyền cho role (xoá toàn bộ quyền cũ rồi thêm mới)
    @PostMapping("/assign")
    @Operation(summary = "Gán mới quyền cho role (xoá toàn bộ quyền cũ rồi thêm mới)")
    public ResponseData<String> assignPermissions(
            @RequestParam Long roleId,
            @RequestBody List<Long> permissionIds) {
        rolePermissionService.assignPermissionsToRole(roleId, permissionIds);
        return new ResponseData<>(200, "Gán quyền thành công (reset)", "OK");
    }

    // Thêm quyền vào role (không xoá quyền cũ)
    @PostMapping("/add")
    @Operation(summary = "Thêm quyền vào role (không xoá quyền cũ)")
    public ResponseData<String> addPermissions(
            @RequestParam Long roleId,
            @RequestBody List<Long> permissionIds) {
        rolePermissionService.addPermissionsToRole(roleId, permissionIds);
        return new ResponseData<>(200, "Thêm quyền thành công", "OK");
    }

    // Xoá quyền cụ thể khỏi role
    @DeleteMapping("/remove")
    @Operation(summary = "Xoá quyền cụ thể khỏi role")
    public ResponseData<String> removePermissions(
            @RequestParam Long roleId,
            @RequestBody List<Long> permissionIds) {
        rolePermissionService.removePermissionsFromRole(roleId, permissionIds);
        return new ResponseData<>(200, "Xoá quyền thành công", "OK");
    }
}
