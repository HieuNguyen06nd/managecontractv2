package com.hieunguyen.ManageContract.controller;

import com.hieunguyen.ManageContract.dto.ResponseData;
import com.hieunguyen.ManageContract.dto.department.DepartmentRequest;
import com.hieunguyen.ManageContract.dto.department.DepartmentResponse;
import com.hieunguyen.ManageContract.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @GetMapping
    @Operation(summary = "Lấy danh sách tất cả phòng ban")
    public ResponseData<List<DepartmentResponse>> getAllDepartments() {
        List<DepartmentResponse> departments = departmentService.getAllDepartments();
        return new ResponseData<>(200, "Lấy danh sách phòng ban thành công", departments);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy thông tin phòng ban theo ID")
    public ResponseData<DepartmentResponse> getDepartmentById(@PathVariable Long id) {
        DepartmentResponse department = departmentService.getDepartmentById(id);
        return new ResponseData<>(200, "Lấy thông tin phòng ban thành công", department);
    }

    @PostMapping
    @Operation(summary = "Tạo phòng ban mới")
    public ResponseData<DepartmentResponse> createDepartment(@RequestBody DepartmentRequest request) {
        DepartmentResponse newDepartment = departmentService.createDepartment(request);
        return new ResponseData<>(201, "Tạo phòng ban thành công", newDepartment);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật phòng ban theo ID")
    public ResponseData<DepartmentResponse> updateDepartment(@PathVariable Long id, @RequestBody DepartmentRequest request) {
        DepartmentResponse updatedDepartment = departmentService.updateDepartment(id, request);
        return new ResponseData<>(200, "Cập nhật phòng ban thành công", updatedDepartment);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa phòng ban theo ID")
    public ResponseData<Void> deleteDepartment(@PathVariable Long id) {
        departmentService.deleteDepartment(id);
        return new ResponseData<>(200, "Xóa phòng ban thành công", null);
    }

    @GetMapping("/paged")
    @Operation(summary = "Lấy danh sách phòng ban có phân trang")
    public ResponseData<Page<DepartmentResponse>> getAllDepartmentsPaged(Pageable pageable) {
        Page<DepartmentResponse> departments = departmentService.getAllDepartments(pageable);
        return new ResponseData<>(200, "Lấy danh sách phòng ban thành công", departments);
    }
}