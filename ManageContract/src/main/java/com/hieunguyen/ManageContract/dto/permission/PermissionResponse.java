package com.hieunguyen.ManageContract.dto.permission;

import lombok.Data;

@Data
public class PermissionResponse {
    private Long permissionId;
    private String permissionKey;
    private String description;
    private String module;
}
