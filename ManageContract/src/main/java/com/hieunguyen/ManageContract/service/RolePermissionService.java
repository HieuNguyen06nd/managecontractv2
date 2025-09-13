package com.hieunguyen.ManageContract.service;

import java.util.List;

public interface RolePermissionService {
    void assignPermissionsToRole(Long roleId, List<Long> permissionIds); // Thêm hoặc thay thế quyền
    void addPermissionsToRole(Long roleId, List<Long> permissionIds);    // Thêm quyền mới (không xoá quyền cũ)
    void removePermissionsFromRole(Long roleId, List<Long> permissionIds); // Xóa một số quyền cụ thể
}
