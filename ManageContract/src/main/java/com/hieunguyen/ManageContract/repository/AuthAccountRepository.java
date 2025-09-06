package com.hieunguyen.ManageContract.repository;

import com.hieunguyen.ManageContract.entity.AuthAccount;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AuthAccountRepository extends JpaRepository<AuthAccount, Long> {

    // Tìm account theo email
    @EntityGraph(attributePaths = {"userRoles", "userRoles.role"})
    Optional<AuthAccount> findByEmail(String email);

    // Tìm account theo mã xác minh email
    Optional<AuthAccount> findByEmailVerificationToken(String token);

    // Kiểm tra email đã tồn tại chưa
    boolean existsByEmail(String email);

    // OAuth2
    @EntityGraph(attributePaths = {"userRoles", "userRoles.role"})
    Optional<AuthAccount> findByGoogleId(String googleId);

    Optional<AuthAccount> findByFacebookId(String facebookId);

    @Query("""
    SELECT a FROM AuthAccount a
    LEFT JOIN FETCH a.userRoles ur
    LEFT JOIN FETCH ur.role r
    LEFT JOIN FETCH r.rolePermissions rp
    LEFT JOIN FETCH rp.permission p
    WHERE a.email = :email
""")
    Optional<AuthAccount> findByEmailWithRolesAndPermissions(@Param("email") String email);

}
