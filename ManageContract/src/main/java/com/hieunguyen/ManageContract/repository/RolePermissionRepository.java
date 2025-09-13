package com.hieunguyen.ManageContract.repository;


import com.hieunguyen.ManageContract.entity.Permission;
import com.hieunguyen.ManageContract.entity.Role;
import com.hieunguyen.ManageContract.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {
    void deleteByRole(Role role);
    boolean existsByRoleAndPermission(Role role, Permission permission);

}