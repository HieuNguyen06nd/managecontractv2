package com.hieunguyen.ManageContract.controller;

import com.hieunguyen.ManageContract.dto.ResponseData;
import com.hieunguyen.ManageContract.dto.permission.PermissionRequest;
import com.hieunguyen.ManageContract.dto.permission.PermissionResponse;
import com.hieunguyen.ManageContract.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    @Operation(summary = "Tạo quyền thao tác hệ thống")
    public ResponseData<PermissionResponse> create(@RequestBody PermissionRequest request) {
        PermissionResponse created = permissionService.create(request);
        return new ResponseData<>(200, "Tạo permission thành công", created);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    @Operation(summary = "Sửa quyền thao tác hệ thống")
    public ResponseData<PermissionResponse> update(@PathVariable Long id, @RequestBody PermissionRequest request) {
        PermissionResponse updated = permissionService.update(id, request);
        return new ResponseData<>(200, "Cập nhật permission thành công", updated);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa quyền thao tác hệ thống")
    public ResponseData<String> delete(@PathVariable Long id) {
        permissionService.delete(id);
        return new ResponseData<>(200, "Xoá permission thành công", null);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    @Operation(summary = "Xem quyền thao tác hệ thống")
    public ResponseData<PermissionResponse> getById(@PathVariable Long id) {
        PermissionResponse result = permissionService.getById(id);
        return new ResponseData<>(200, "Lấy permission theo ID thành công", result);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    @Operation(summary = "Xem tất cả quyền thao tác hệ thống")
    public ResponseData<List<PermissionResponse>> getAll() {
        List<PermissionResponse> list = permissionService.getAll();
        return new ResponseData<>(200, "Lấy tất cả permission thành công", list);
    }
}
