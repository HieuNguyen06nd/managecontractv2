package com.hieunguyen.ManageContract.mapper;

import com.hieunguyen.ManageContract.dto.authAccount.AuthProfileResponse;
import com.hieunguyen.ManageContract.dto.permission.PermissionResponse;
import com.hieunguyen.ManageContract.dto.role.RoleResponse;
import com.hieunguyen.ManageContract.dto.user.UserResponse;
import com.hieunguyen.ManageContract.dto.user.UserUpdateRequest;
import com.hieunguyen.ManageContract.entity.AuthAccount;
import com.hieunguyen.ManageContract.entity.Department;
import com.hieunguyen.ManageContract.entity.Employee;
import com.hieunguyen.ManageContract.entity.Position;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {
    // Tạo UserResponse từ entity User và AuthAccount
    public AuthProfileResponse toUserResponse(Employee employee, AuthAccount account) {
        if (employee == null || account == null) return null;

        var roleResponses = account.getUserRoles().stream()
                .map(userRole -> {
                    var role = userRole.getRole();

                    var permissions = role.getRolePermissions().stream()
                            .map(rp -> new PermissionResponse(
                                    rp.getPermission().getId(),
                                    rp.getPermission().getPermissionKey(),
                                    rp.getPermission().getDescription(),
                                    rp.getPermission().getModule()
                            ))
                            .toList();

                    return new RoleResponse(
                            role.getId(),
                            role.getRoleKey(),
                            role.getDescription(),
                            permissions
                    );
                })
                .toList();

        Department dept = employee.getDepartment();
        Position pos = employee.getPosition();

        Long departmentId = (dept != null) ? dept.getId() : null;
        String departmentName = (dept != null) ? dept.getName() : null;

        Long positionId = (pos != null) ? pos.getId() : null;
        String positionName = (pos != null) ? pos.getName() : null;

        return AuthProfileResponse.builder()
                .id(employee.getId())
                .fullName(employee.getFullName())
                .phone(employee.getPhone())
                .gender(employee.getGender())
                .email(account.getEmail())
                .roles(roleResponses)

                .departmentId(departmentId)
                .departmentName(departmentName)
                .positionId(positionId)
                .positionName(positionName)
                .build();
    }


    // Cập nhật entity User từ request
    public void updateUserFromRequest(Employee employee, UserUpdateRequest request) {
        if (request.getFullName() != null) {
            employee.setFullName(request.getFullName());
        }

        if (request.getPhone() != null) {
            employee.setPhone(request.getPhone());
        }

        if (request.getGender() != null) {
            employee.setGender(request.getGender());
        }
    }
}
