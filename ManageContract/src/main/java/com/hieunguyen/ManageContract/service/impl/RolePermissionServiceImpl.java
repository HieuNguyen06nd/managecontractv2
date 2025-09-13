package com.hieunguyen.ManageContract.service.impl;

import com.hieunguyen.ManageContract.common.exception.ResourceNotFoundException;
import com.hieunguyen.ManageContract.entity.Permission;
import com.hieunguyen.ManageContract.entity.Role;
import com.hieunguyen.ManageContract.entity.RolePermission;
import com.hieunguyen.ManageContract.repository.PermissionRepository;
import com.hieunguyen.ManageContract.repository.RolePermissionRepository;
import com.hieunguyen.ManageContract.repository.RoleRepository;
import com.hieunguyen.ManageContract.service.RolePermissionService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RolePermissionServiceImpl implements RolePermissionService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;

    @Override
    @Transactional
    public void assignPermissionsToRole(Long roleId, List<Long> permissionIds) {
        Role role = getRoleById(roleId);

        // Xoá hết quyền cũ
        rolePermissionRepository.deleteByRole(role);

        // Gán mới toàn bộ
        List<RolePermission> newPermissions = permissionIds.stream()
                .map(pid -> buildRolePermission(role, getPermissionById(pid)))
                .collect(Collectors.toList());

        rolePermissionRepository.saveAll(newPermissions);
    }

    @Override
    @Transactional
    public void addPermissionsToRole(Long roleId, List<Long> permissionIds) {
        Role role = getRoleById(roleId);

        // Lấy các permissionId đã tồn tại
        Set<Long> existingIds = role.getRolePermissions().stream()
                .map(rp -> rp.getPermission().getId())
                .collect(Collectors.toSet());

        // Chỉ thêm những quyền chưa có
        List<RolePermission> toAdd = permissionIds.stream()
                .filter(pid -> !existingIds.contains(pid))
                .map(pid -> buildRolePermission(role, getPermissionById(pid)))
                .collect(Collectors.toList());

        rolePermissionRepository.saveAll(toAdd);
    }

    @Override
    @Transactional
    public void removePermissionsFromRole(Long roleId, List<Long> permissionIds) {
        Role role = getRoleById(roleId);

        // Lọc các quyền cần xóa
        List<RolePermission> toRemove = role.getRolePermissions().stream()
                .filter(rp -> permissionIds.contains(rp.getPermission().getId()))
                .toList();

        rolePermissionRepository.deleteAll(toRemove);
    }

    // ===================== PRIVATE SUPPORT METHODS =====================

    private Role getRoleById(Long roleId) {
        return roleRepository.findByIdWithPermissions(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + roleId));
    }

    private Permission getPermissionById(Long permissionId) {
        return permissionRepository.findById(permissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found with id: " + permissionId));
    }

    private RolePermission buildRolePermission(Role role, Permission permission) {
        RolePermission rp = new RolePermission();
        rp.setRole(role);
        rp.setPermission(permission);
        rp.setCreatedAt(LocalDateTime.now());
        return rp;
    }
}
