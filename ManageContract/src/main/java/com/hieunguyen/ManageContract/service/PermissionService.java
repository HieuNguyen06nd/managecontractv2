package com.hieunguyen.ManageContract.service;

import com.hieunguyen.ManageContract.dto.permission.PermissionRequest;
import com.hieunguyen.ManageContract.dto.permission.PermissionResponse;

import java.util.List;

public interface PermissionService {
    PermissionResponse create(PermissionRequest request);
    PermissionResponse update(Long id, PermissionRequest request);
    void delete(Long id);
    PermissionResponse getById(Long id);
    List<PermissionResponse> getAll();
}
