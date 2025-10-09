package com.hieunguyen.ManageContract.controller;

import com.hieunguyen.ManageContract.dto.ResponseData;
import com.hieunguyen.ManageContract.dto.authAccount.AuthProfileResponse;
import com.hieunguyen.ManageContract.dto.user.UserUpdateRequest;
import com.hieunguyen.ManageContract.service.UserService;
import com.hieunguyen.ManageContract.service.AuthAccountService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.BadRequestException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthAccountService authAccountService;

    // Cập nhật thông tin tài khoản user
    @PutMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Sửa thông tin tài khoản user")
    public ResponseData<AuthProfileResponse> updateUserProfile(@RequestBody UserUpdateRequest request) throws BadRequestException {
        AuthProfileResponse updated = userService.updateCurrentUser(request);
        return new ResponseData<>(200, "Cập nhật hồ sơ thành công", updated);
    }

    // Xem thông tin tài khoản user
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xem thông tin tài khoản user")
    public ResponseData<AuthProfileResponse> getMyProfile() {
        var profile = authAccountService.getMyProfile();
        return new ResponseData<>(200, "Lấy thông tin cá nhân thành công", profile);
    }

    // Lấy danh sách tất cả tài khoản
    @GetMapping("/all")
    @Operation(summary = "Lấy danh sách tất cả tài khoản")
    public ResponseData<List<AuthProfileResponse>> getAllUsers() {
        List<AuthProfileResponse> users = authAccountService.getAll();
        return new ResponseData<>(200, "Lấy danh sách tài khoản thành công", users);
    }

    // Upload Avatar cho người dùng
    @PostMapping("/me/avatar")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Upload ảnh Avatar cho user")
    public ResponseData<AuthProfileResponse> uploadAvatar(@RequestParam("file") MultipartFile file) throws BadRequestException {
        AuthProfileResponse response = userService.uploadAvatar(file);
        return new ResponseData<>(200, "Upload ảnh Avatar thành công", response);
    }

    // Upload Chữ ký cho người dùng
    @PostMapping("/me/signature")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Upload chữ ký cho user")
    public ResponseData<AuthProfileResponse> uploadSignature(@RequestParam("file") MultipartFile file) throws BadRequestException {
        AuthProfileResponse response = userService.uploadSignature(file);
        return new ResponseData<>(200, "Upload chữ ký thành công", response);
    }

    // Upload chữ ký từ Base64 cho người dùng
    @PostMapping("/me/signature/base64")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Upload chữ ký từ Base64 cho user")
    public ResponseData<AuthProfileResponse> uploadSignatureBase64(@RequestBody Map<String, String> body) throws BadRequestException {
        String dataUrl = body.get("dataUrl");
        AuthProfileResponse response = userService.uploadSignatureBase64(dataUrl);
        return new ResponseData<>(200, "Upload chữ ký Base64 thành công", response);
    }
}
