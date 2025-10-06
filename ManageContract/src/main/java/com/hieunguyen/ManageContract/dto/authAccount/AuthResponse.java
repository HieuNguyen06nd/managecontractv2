// dto/authAccount/AuthResponse.java
package com.hieunguyen.ManageContract.dto.authAccount;

import com.hieunguyen.ManageContract.dto.role.RoleResponse;
import java.util.List;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        Long accountId,
        List<RoleResponse> roles,
        Boolean requirePasswordChange,
        String changePasswordToken
) {
    // factories tiện dùng
    public static AuthResponse requireChange(Long accountId, String changeToken) {
        return new AuthResponse(null, null, accountId, List.of(), true, changeToken);
    }
    public static AuthResponse normal(String access, String refresh, Long accountId, List<RoleResponse> roles) {
        return new AuthResponse(access, refresh, accountId, roles, false, null);
    }
}
