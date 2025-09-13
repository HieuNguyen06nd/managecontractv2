package com.hieunguyen.ManageContract.dto.authAccount;

import com.hieunguyen.ManageContract.dto.role.RoleResponse;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private long userId;
    private List<RoleResponse> roles;
}
