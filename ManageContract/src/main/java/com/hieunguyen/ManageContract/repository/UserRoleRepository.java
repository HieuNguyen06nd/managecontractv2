package com.hieunguyen.ManageContract.repository;

import com.hieunguyen.ManageContract.entity.AuthAccount;
import com.hieunguyen.ManageContract.entity.Role;
import com.hieunguyen.ManageContract.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    // Tìm danh sách vai trò theo tài khoản
    List<UserRole> findByAccount(AuthAccount account);

    // Tìm cụ thể 1 vai trò của tài khoản
    Optional<UserRole> findByAccountAndRole(AuthAccount account, Role role);

    // Kiểm tra xem tài khoản đã có role cụ thể chưa
    boolean existsByAccountAndRole_RoleKey(AuthAccount account, String roleKey);

    // Xoá tất cả quyền của 1 account (nếu cần reset role)
    void deleteAllByAccount(AuthAccount account);

    boolean existsByAccountAndRole(AuthAccount account, Role role);

}
