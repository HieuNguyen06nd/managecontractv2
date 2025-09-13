package com.hieunguyen.ManageContract.dto.role;

import com.hieunguyen.ManageContract.dto.permission.PermissionResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleResponse {
    private Long roleId;
    private String roleKey;
    private String description;
    private List<PermissionResponse> permissions;

    public RoleResponse(String roleKey, String description) {
        this.roleKey = roleKey;
        this.description = description;
    }

}
