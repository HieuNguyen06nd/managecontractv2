package com.hieunguyen.ManageContract.service.impl;

import com.hieunguyen.ManageContract.common.exception.ResourceNotFoundException;
import com.hieunguyen.ManageContract.dto.authAccount.AuthProfileResponse;
import com.hieunguyen.ManageContract.dto.user.UserResponse;
import com.hieunguyen.ManageContract.dto.user.UserUpdateRequest;
import com.hieunguyen.ManageContract.entity.AuthAccount;
import com.hieunguyen.ManageContract.entity.Employee;
import com.hieunguyen.ManageContract.mapper.UserMapper;
import com.hieunguyen.ManageContract.repository.AuthAccountRepository;
import com.hieunguyen.ManageContract.repository.UserRepository;
import com.hieunguyen.ManageContract.security.jwt.SecurityUtil;
import com.hieunguyen.ManageContract.service.FileUploadService;
import com.hieunguyen.ManageContract.service.UserService;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.BadRequestException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@RequiredArgsConstructor
@Service
public class UserServiceImpl implements UserService {

    private final AuthAccountRepository authAccountRepository;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final FileUploadService fileUploadService;

    @Override
    public AuthProfileResponse updateCurrentUser(UserUpdateRequest request) throws BadRequestException {
        String email = SecurityUtil.getCurrentUserEmail();

        AuthAccount account = authAccountRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản"));


        Employee employee = account.getEmployee();
        if (employee == null) {
            throw new ResourceNotFoundException("Không tìm thấy thông tin người dùng");
        }

        userMapper.updateUserFromRequest(employee, request);

        userRepository.save(employee);
        return userMapper.toUserResponse(employee, account);
    }

    @Override
    public AuthProfileResponse uploadSignature(MultipartFile signature) throws BadRequestException {
        if (signature == null || signature.isEmpty()) throw new BadRequestException("Chọn ảnh chữ ký");
        try {
            Employee e = currentEmployee();
            String relPath = fileUploadService.saveMultipart(e.getId(), signature, "signatures");
            e.setSignatureImage(relPath);
            e.setSignatureUpdatedAt(LocalDateTime.now());
            userRepository.save(e);
            return userMapper.toUserResponse(e, e.getAccount());
        } catch (Exception ex) {
            throw new BadRequestException("Lỗi upload chữ ký: " + ex.getMessage());
        }
    }

    @Override
    public AuthProfileResponse uploadSignatureBase64(String dataUrl) throws BadRequestException {
        if (dataUrl == null || dataUrl.isBlank()) throw new BadRequestException("Thiếu dữ liệu base64");
        try {
            Employee e = currentEmployee();
            String relPath = fileUploadService.saveBase64(e.getId(), dataUrl, "signatures", "png");
            e.setSignatureImage(relPath);
            e.setSignatureUpdatedAt(LocalDateTime.now());
            userRepository.save(e);
            return userMapper.toUserResponse(e, e.getAccount());
        } catch (Exception ex) {
            throw new BadRequestException("Lỗi upload chữ ký (base64): " + ex.getMessage());
        }
    }

    @Override
    public AuthProfileResponse uploadAvatar(MultipartFile avatar) throws BadRequestException {
        if (avatar == null || avatar.isEmpty()) throw new BadRequestException("Chọn ảnh avatar");
        try {
            Employee e = currentEmployee();  // Sử dụng phương thức này để lấy Employee hiện tại
            String relPath = fileUploadService.saveMultipart(e.getId(), avatar, "avatars");
            e.setAvatarImage(relPath);
            userRepository.save(e);
            return userMapper.toUserResponse(e, e.getAccount());
        } catch (Exception ex) {
            throw new BadRequestException("Lỗi upload avatar: " + ex.getMessage());
        }
    }

    private Employee currentEmployee() {
        String email = SecurityUtil.getCurrentUserEmail(); // Lấy email của người dùng hiện tại từ SecurityUtil
        return userRepository.findByAccount_Email(email)
                .orElseThrow(() -> new RuntimeException("Người dùng không tồn tại"));
    }

}