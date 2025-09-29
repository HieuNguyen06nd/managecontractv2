package com.hieunguyen.ManageContract.mapper;

import com.hieunguyen.ManageContract.dto.permission.PermissionResponse;
import com.hieunguyen.ManageContract.dto.role.RoleRequest;
import com.hieunguyen.ManageContract.dto.role.RoleResponse;
import com.hieunguyen.ManageContract.entity.Permission;
import com.hieunguyen.ManageContract.entity.Role;
import com.hieunguyen.ManageContract.entity.RolePermission;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class RoleMapper {
    public Role toEntity(RoleRequest request) {
        Role role = new Role();
        role.setRoleKey(request.getRoleKey());
        role.setDescription(request.getDescription());
        return role;
    }

    public RoleResponse toResponse(Role role) {
        List<PermissionResponse> permissionResponses = role.getRolePermissions() != null
                ? role.getRolePermissions().stream()
                .map(RolePermission::getPermission)
                .map(this::toPermissionResponse)
                .collect(Collectors.toList())
                : List.of(); // Nếu rolePermissions null thì trả về list rỗng

        return new RoleResponse(
                role.getId(),
                role.getRoleKey(),
                role.getDescription(),
                permissionResponses
        );
    }

    private PermissionResponse toPermissionResponse(Permission permission) {
        PermissionResponse response = new PermissionResponse();
        response.setId(permission.getId());
        response.setPermissionKey(permission.getPermissionKey());
        response.setDescription(permission.getDescription());
        response.setModule(permission.getModule());
        return response;
    }

    public void updateRoleFromRequest(Role role, RoleRequest request) {
        role.setRoleKey(request.getRoleKey());
        role.setDescription(request.getDescription());
    }
}
