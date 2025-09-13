package com.hieunguyen.ManageContract.repository;

import com.hieunguyen.ManageContract.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
    boolean existsByPermissionKey(String permissionKey);
}
