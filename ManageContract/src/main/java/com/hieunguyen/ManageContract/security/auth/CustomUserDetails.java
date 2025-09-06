package com.hieunguyen.ManageContract.security.auth;

import com.hieunguyen.ManageContract.common.constants.StatusUser;
import com.hieunguyen.ManageContract.entity.AuthAccount;
import com.hieunguyen.ManageContract.entity.RolePermission;
import com.hieunguyen.ManageContract.entity.UserRole;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class CustomUserDetails implements UserDetails {

    @Getter
    private final AuthAccount account;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(AuthAccount account) {
        this.account = account;

        // Load quyền và vai trò vào danh sách GrantedAuthority
        Set<SimpleGrantedAuthority> authoritySet = new HashSet<>();

        for (UserRole userRole : account.getUserRoles()) {
            if (userRole.getRole() != null) {
                // 1. Thêm role dưới dạng ROLE_xxx
                String roleKey = userRole.getRole().getRoleKey();
                if (roleKey != null && !roleKey.isBlank()) {
                    authoritySet.add(new SimpleGrantedAuthority("ROLE_" + roleKey.trim().toUpperCase()));
                }

                // 2. Thêm các permission từ role
                if (userRole.getRole().getRolePermissions() != null) {
                    for (RolePermission rolePermission : userRole.getRole().getRolePermissions()) {
                        if (rolePermission.getPermission() != null) {
                            String permissionKey = rolePermission.getPermission().getPermissionKey();
                            if (permissionKey != null && !permissionKey.isBlank()) {
                                authoritySet.add(new SimpleGrantedAuthority(permissionKey.trim()));
                            }
                        }
                    }
                }
            }
        }

        this.authorities = authoritySet;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {

        return authorities;
    }
    @Override
    public String getPassword() {
        return account.getPassword();
    }

    @Override
    public String getUsername() {
        return account.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {

        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return account.getStatus() == StatusUser.ACTIVE;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return account.getStatus() == StatusUser.ACTIVE;
    }

    public Long getAccountId() {
        return account.getId();
    }

}