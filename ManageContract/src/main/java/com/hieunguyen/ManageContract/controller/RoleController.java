package com.hieunguyen.ManageContract.controller;

import com.hieunguyen.ManageContract.dto.ResponseData;
import com.hieunguyen.ManageContract.dto.role.RoleRequest;
import com.hieunguyen.ManageContract.dto.role.RoleResponse;
import com.hieunguyen.ManageContract.service.RolePermissionService;
import com.hieunguyen.ManageContract.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;
    private final RolePermissionService rolePermissionService;

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_CREATE')")
    @Operation(summary = "Tạo nhóm quyền người dùng")
    public ResponseData<RoleResponse> create(@io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Body chứa thông tin user",
            required = true,
            content = @Content(schema = @Schema(implementation = RoleRequest.class))
    ) @RequestBody RoleRequest request) {
        Object o = SecurityContextHolder.getContext().getAuthentication().getAuthorities();
        System.out.println(o);
        RoleResponse result = roleService.createRole(request);
        return new ResponseData<>(200, "Tạo vai trò thành công", result);
    }

    @GetMapping
    @Operation(summary = "Xem danh sách nhóm quyền người dùng")
    public ResponseData<List<RoleResponse>> getAll() {
        List<RoleResponse> roles = roleService.getAllRoles();
        return new ResponseData<>(200, "Danh sách vai trò", roles);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem 1 nhóm quyền người dùng")
    public ResponseData<RoleResponse> getById(@PathVariable Long id) {
        RoleResponse role = roleService.getRoleById(id);
        return new ResponseData<>(200, "Chi tiết vai trò", role);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('role.update')")
    @Operation(summary = "Sửa nhóm quyền người dùng")
    public ResponseData<RoleResponse> update(@PathVariable Long id, @RequestBody RoleRequest request) {
        RoleResponse updated = roleService.updateRole(id, request);
        return new ResponseData<>(200, "Cập nhật vai trò thành công", updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('role.delete')")
    @Operation(summary = "Xóa nhóm quyền người dùng")
    public ResponseData<Void> delete(@PathVariable Long id) {
        roleService.deleteRole(id);
        return new ResponseData<>(200, "Xóa vai trò thành công", null);
    }


}
