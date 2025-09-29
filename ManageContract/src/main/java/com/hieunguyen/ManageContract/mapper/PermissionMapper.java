package com.hieunguyen.ManageContract.mapper;

import com.hieunguyen.ManageContract.dto.permission.PermissionRequest;
import com.hieunguyen.ManageContract.dto.permission.PermissionResponse;
import com.hieunguyen.ManageContract.entity.Permission;
import org.springframework.stereotype.Component;

@Component
public class PermissionMapper {

    public Permission toEntity(PermissionRequest request) {
        Permission permission = new Permission();
        permission.setPermissionKey(request.getPermissionKey());
        permission.setDescription(request.getDescription());
        permission.setModule(request.getModule());
        return permission;
    }

    public PermissionResponse toResponse(Permission entity) {
        PermissionResponse response = new PermissionResponse();
        response.setId(entity.getId());
        response.setPermissionKey(entity.getPermissionKey());
        response.setDescription(entity.getDescription());
        response.setModule(entity.getModule());
        return response;
    }

    public void updateEntity(Permission entity, PermissionRequest request) {
        entity.setPermissionKey(request.getPermissionKey());
        entity.setDescription(request.getDescription());
        entity.setModule(request.getModule());
    }
}
