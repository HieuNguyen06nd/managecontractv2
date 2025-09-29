package com.hieunguyen.ManageContract.repository;

import com.hieunguyen.ManageContract.entity.AuthAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AuthAccountRepository extends JpaRepository<AuthAccount, Long>, JpaSpecificationExecutor<AuthAccount> {

    // Tìm account theo email (fetch roles)
    @EntityGraph(attributePaths = {"userRoles", "userRoles.role"})
    Optional<AuthAccount> findByEmail(String email);

    // Tìm account theo mã xác minh email
    Optional<AuthAccount> findByEmailVerificationToken(String token);

    // Kiểm tra email đã tồn tại chưa
    boolean existsByEmail(String email);

    // OAuth2 - load account + roles
    @EntityGraph(attributePaths = {"userRoles", "userRoles.role"})
    Optional<AuthAccount> findByGoogleId(String googleId);

    Optional<AuthAccount> findByFacebookId(String facebookId);

    // Load account + roles + permissions (tránh LazyInitializationException)
    @Query("""
        SELECT DISTINCT a FROM AuthAccount a
        LEFT JOIN FETCH a.userRoles ur
        LEFT JOIN FETCH ur.role r
        LEFT JOIN FETCH r.rolePermissions rp
        LEFT JOIN FETCH rp.permission p
        WHERE a.email = :email
    """)
    Optional<AuthAccount> findByEmailWithRolesAndPermissions(@Param("email") String email);


    // Thêm phương thức mới để lấy tất cả tài khoản cùng với vai trò và quyền
    @Query("SELECT DISTINCT a FROM AuthAccount a " +
            "LEFT JOIN FETCH a.userRoles ur " +
            "LEFT JOIN FETCH ur.role r " +
            "LEFT JOIN FETCH r.rolePermissions rp " +
            "LEFT JOIN FETCH rp.permission p")
    List<AuthAccount> findAllWithRolesAndPermissions();

    @Query("""
           SELECT a FROM AuthAccount a
           LEFT JOIN FETCH a.employee e
           LEFT JOIN FETCH e.department d
           LEFT JOIN FETCH e.position p
           """)
    Page<AuthAccount> findAllWithEmployee(Pageable pageable);



}
