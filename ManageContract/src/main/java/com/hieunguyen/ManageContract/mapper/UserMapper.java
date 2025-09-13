package com.hieunguyen.ManageContract.mapper;

import com.hieunguyen.ManageContract.dto.user.UserResponse;
import com.hieunguyen.ManageContract.dto.user.UserUpdateRequest;
import com.hieunguyen.ManageContract.entity.AuthAccount;
import com.hieunguyen.ManageContract.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {
    // Tạo UserResponse từ entity User và AuthAccount
    public UserResponse toUserResponse(User user, AuthAccount account) {
        if (user == null || account == null) return null;

        var roles = account.getUserRoles().stream()
                .map(userRole -> userRole.getRole().getRoleKey())
                .toList();

        return UserResponse.builder()
                .userId(user.getId())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .gender(user.getGender())
                .email(account.getEmail())
                .roles(roles)
                .build();
    }

    // Cập nhật entity User từ request
    public void updateUserFromRequest(User user, UserUpdateRequest request) {
        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }

        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }

        if (request.getGender() != null) {
            user.setGender(request.getGender());
        }
    }
}
