package com.hieunguyen.ManageContract.repository;

import com.hieunguyen.ManageContract.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    // Tìm role theo key (admin, doctor, staff,...)
    Optional<Role> findByRoleKey(String roleKey);

    // Kiểm tra role có tồn tại không
    boolean existsByRoleKey(String roleKey);

    Optional<Role> findByRoleKeyIgnoreCase(String roleKey);

    @Query("""
    SELECT r FROM Role r
    LEFT JOIN FETCH r.rolePermissions rp
    LEFT JOIN FETCH rp.permission
    WHERE r.id = :id
""")
    Optional<Role> findByIdWithPermissions(@Param("id") Long id);

}
