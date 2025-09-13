package com.hieunguyen.ManageContract.dto.permission;

import lombok.Data;

@Data
public class PermissionRequest {
    private String permissionKey;
    private String description;
    private String module;
}
