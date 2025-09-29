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

@Getter
public class CustomUserDetails implements UserDetails {

    private final Long accountId;
    private final Long employeeId; // có thể null nếu chưa link
    private final String email;
    private final String password;
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(AuthAccount account) {
        this.accountId = account.getId();
        this.employeeId = account.getEmployee() != null ? account.getEmployee().getId() : null;
        this.email = account.getEmail();
        this.password = account.getPassword();
        this.enabled = account.getStatus() == StatusUser.ACTIVE;

        // build authorities từ roles/permissions
        Set<SimpleGrantedAuthority> auths = new HashSet<>();
        for (UserRole userRole : account.getUserRoles()) {
            if (userRole.getRole() != null) {
                String roleKey = userRole.getRole().getRoleKey();
                if (roleKey != null && !roleKey.isBlank()) {
                    auths.add(new SimpleGrantedAuthority("ROLE_" + roleKey.trim().toUpperCase()));
                }
                if (userRole.getRole().getRolePermissions() != null) {
                    for (RolePermission rp : userRole.getRole().getRolePermissions()) {
                        if (rp.getPermission() != null) {
                            String pKey = rp.getPermission().getPermissionKey();
                            if (pKey != null && !pKey.isBlank()) {
                                auths.add(new SimpleGrantedAuthority(pKey.trim()));
                            }
                        }
                    }
                }
            }
        }
        this.authorities = auths;
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return email; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return enabled; } // hoặc tự tách trạng thái LOCKED
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
}
