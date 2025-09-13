package com.hieunguyen.ManageContract.service.impl;

import com.hieunguyen.ManageContract.common.exception.ResourceNotFoundException;
import com.hieunguyen.ManageContract.dto.role.RoleRequest;
import com.hieunguyen.ManageContract.dto.role.RoleResponse;
import com.hieunguyen.ManageContract.entity.Role;
import com.hieunguyen.ManageContract.mapper.RoleMapper;
import com.hieunguyen.ManageContract.repository.RoleRepository;
import com.hieunguyen.ManageContract.service.RoleService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final RoleMapper roleMapper;

    @Override
    public RoleResponse createRole(RoleRequest request) {
        // Kiểm tra trùng roleKey nếu cần
        if (roleRepository.existsByRoleKey(request.getRoleKey())) {
            throw new IllegalArgumentException("Role đã tồn tại: " + request.getRoleKey());
        }

        Role role = roleMapper.toEntity(request);
        roleRepository.save(role);

        return roleMapper.toResponse(role);
    }
    @Override
    public RoleResponse getRoleById(Long id) {
        Role role = roleRepository.findByIdWithPermissions(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + id));
        return roleMapper.toResponse(role);
    }

    @Override
    public List<RoleResponse> getAllRoles() {
        List<Role> roles = roleRepository.findAll();
        return roles.stream().map(roleMapper::toResponse).toList();
    }

    @Override
    public RoleResponse updateRole(Long id, RoleRequest request) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + id));
        roleMapper.updateRoleFromRequest(role, request);
        roleRepository.save(role);
        return roleMapper.toResponse(role);
    }
    @Override
    @Transactional
    public void deleteRole(Long id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + id));
        roleRepository.delete(role);
    }
}
