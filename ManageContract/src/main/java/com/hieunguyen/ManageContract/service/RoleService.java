package com.hieunguyen.ManageContract.service;

import com.hieunguyen.ManageContract.dto.role.RoleRequest;
import com.hieunguyen.ManageContract.dto.role.RoleResponse;

import java.util.List;

public interface RoleService {
    RoleResponse createRole(RoleRequest request);

    RoleResponse updateRole(Long roleId, RoleRequest request);

    void deleteRole(Long roleId);

    RoleResponse getRoleById(Long roleId);

    List<RoleResponse> getAllRoles();
}