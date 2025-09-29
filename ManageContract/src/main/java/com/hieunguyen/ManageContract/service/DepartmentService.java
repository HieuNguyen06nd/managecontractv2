package com.hieunguyen.ManageContract.service;

import com.hieunguyen.ManageContract.dto.department.DepartmentRequest;
import com.hieunguyen.ManageContract.dto.department.DepartmentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface DepartmentService {
    List<DepartmentResponse> getAllDepartments();
    DepartmentResponse getDepartmentById(Long id);
    DepartmentResponse createDepartment(DepartmentRequest request);
    DepartmentResponse updateDepartment(Long id, DepartmentRequest request);
    void deleteDepartment(Long id);
    Page<DepartmentResponse> getAllDepartments(Pageable pageable);
}