package com.hieunguyen.ManageContract.controller;

import com.hieunguyen.ManageContract.dto.ResponseData;
import com.hieunguyen.ManageContract.dto.authAccount.AuthProfileResponse;
import com.hieunguyen.ManageContract.dto.user.UserUpdateRequest;
import com.hieunguyen.ManageContract.service.AuthAccountService;
import com.hieunguyen.ManageContract.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.BadRequestException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthAccountService authAccountService;

    @PutMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Sửa thông tin tài khoản user")
    public ResponseData<AuthProfileResponse> updateUserProfile(@RequestBody UserUpdateRequest request) throws BadRequestException {
        AuthProfileResponse updated = userService.updateCurrentUser(request);
        return new ResponseData<>(200, "Cập nhật hồ sơ thành công", updated);
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xem thông tin tài khoản user")
    public ResponseData<AuthProfileResponse> getMyProfile() {
        var profile = authAccountService.getMyProfile();
        return new ResponseData<>(200, "Lấy thông tin cá nhân thành công", profile);
    }

    @GetMapping("/all")
    @Operation(summary = "Lấy danh sách tất cả tài khoản")
    public ResponseData<List<AuthProfileResponse>> getAllUsers() {
        List<AuthProfileResponse> users = authAccountService.getAll();
        return new ResponseData<>(200, "Lấy danh sách tài khoản thành công", users);
    }
}