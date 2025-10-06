package com.hieunguyen.ManageContract.service.impl;

import com.hieunguyen.ManageContract.common.exception.ResourceNotFoundException;
import com.hieunguyen.ManageContract.dto.department.DepartmentRequest;
import com.hieunguyen.ManageContract.dto.department.DepartmentResponse;
import com.hieunguyen.ManageContract.entity.Department;
import com.hieunguyen.ManageContract.entity.Employee;
import com.hieunguyen.ManageContract.repository.DepartmentRepository;
import com.hieunguyen.ManageContract.repository.UserRepository;
import com.hieunguyen.ManageContract.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final UserRepository employeeRepository;

    @Override
    public List<DepartmentResponse> getAllDepartments() {
        return departmentRepository.findAllWithParentAndLeader().stream()
                .map(this::mapDeptToUIWithEmployeeCount)  // Tính số lượng nhân viên qua query
                .collect(Collectors.toList());
    }

    @Override
    public DepartmentResponse getDepartmentById(Long id) {
        Department department = departmentRepository.findByIdWithParentAndLeader(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + id));
        return mapToDepartmentResponse(department);
    }

    @Override
    public DepartmentResponse createDepartment(DepartmentRequest request) {
        Department department = new Department();
        department.setName(request.getName());
        department.setLevel(request.getLevel());

        if (request.getParentId() != null) {
            Department parent = departmentRepository.findById(request.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent department not found with id: " + request.getParentId()));
            department.setParent(parent);
        }

        if (request.getLeaderId() != null) {
            Employee leader = employeeRepository.findById(request.getLeaderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Leader not found with id: " + request.getLeaderId()));
            department.setLeader(leader);
        }
        department.setStatus(request.getStatus());

        Department savedDepartment = departmentRepository.save(department);
        return mapToDepartmentResponse(savedDepartment);
    }

    @Override
    public DepartmentResponse updateDepartment(Long id, DepartmentRequest request) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + id));

        department.setName(request.getName());
        department.setLevel(request.getLevel());
        department.setStatus(request.getStatus());

        if (request.getParentId() != null) {
            Department parent = departmentRepository.findById(request.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent department not found with id: " + request.getParentId()));
            department.setParent(parent);
        } else {
            department.setParent(null);
        }

        if (request.getLeaderId() != null) {
            Employee leader = employeeRepository.findById(request.getLeaderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Leader not found with id: " + request.getLeaderId()));
            department.setLeader(leader);
        } else {
            department.setLeader(null);
        }

        Department updatedDepartment = departmentRepository.save(department);
        return mapToDepartmentResponse(updatedDepartment);
    }

    public Page<DepartmentResponse> getAllDepartments(Pageable pageable) {
        Page<Department> pagedDepartments = departmentRepository.findAllWithParentAndLeaderAndEmployees(pageable);
        return pagedDepartments.map(this::mapToDepartmentResponse);
    }

    @Override
    public void deleteDepartment(Long id) {
        if (!departmentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Department not found with id: " + id);
        }
        departmentRepository.deleteById(id);
    }

    // Phương thức ánh xạ thông tin phòng ban từ entity -> DTO, không tính số lượng nhân viên
    private DepartmentResponse mapToDepartmentResponse(Department department) {
        String parentName = department.getParent() != null ? department.getParent().getName() : null;
        String leaderName = department.getLeader() != null ? department.getLeader().getFullName() : null;

        // Sử dụng query để đếm số lượng nhân viên từ cơ sở dữ liệu
        Long employeeCount = employeeRepository.countEmployeesByDepartmentId(department.getId());

        return DepartmentResponse.builder()
                .id(department.getId())
                .name(department.getName())
                .level(department.getLevel())
                .parentId(department.getParent() != null ? department.getParent().getId() : null)
                .parentName(parentName)
                .leaderId(department.getLeader() != null ? department.getLeader().getId() : null)
                .leaderName(leaderName)
                .employeeCount(employeeCount.intValue())  // Sử dụng giá trị long từ query
                .status(department.getStatus())
                .build();
    }

    // Phương thức ánh xạ phòng ban từ entity -> DTO, tính số lượng nhân viên
    private DepartmentResponse mapDeptToUIWithEmployeeCount(Department department) {
        String parentName = department.getParent() != null ? department.getParent().getName() : null;
        String leaderName = department.getLeader() != null ? department.getLeader().getFullName() : null;

        // Đếm số lượng nhân viên từ cơ sở dữ liệu qua query
        Long employeeCount = employeeRepository.countEmployeesByDepartmentId(department.getId());

        return DepartmentResponse.builder()
                .id(department.getId())
                .name(department.getName())
                .level(department.getLevel())
                .parentId(department.getParent() != null ? department.getParent().getId() : null)
                .parentName(parentName)
                .leaderId(department.getLeader() != null ? department.getLeader().getId() : null)
                .leaderName(leaderName)
                .employeeCount(employeeCount.intValue())  // Sử dụng giá trị long từ query
                .status(department.getStatus())
                .build();
    }
}
