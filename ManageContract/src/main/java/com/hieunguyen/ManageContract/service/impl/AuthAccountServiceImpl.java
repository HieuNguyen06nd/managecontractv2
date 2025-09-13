package com.hieunguyen.ManageContract.service.impl;

import com.hieunguyen.ManageContract.common.exception.ResourceNotFoundException;
import com.hieunguyen.ManageContract.dto.authAccount.AuthProfileResponse;
import com.hieunguyen.ManageContract.entity.AuthAccount;
import com.hieunguyen.ManageContract.repository.AuthAccountRepository;
import com.hieunguyen.ManageContract.security.jwt.SecurityUtil;
import com.hieunguyen.ManageContract.service.AuthAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthAccountServiceImpl implements AuthAccountService {

    private final AuthAccountRepository authAccountRepository;

    @Override
    public AuthAccount findByEmailOrPhone(String emailOrPhone) {
        Optional<AuthAccount> accountOpt = authAccountRepository
                .findByEmail(emailOrPhone);

        if (accountOpt.isEmpty()) {
            throw new ResourceNotFoundException("Tài khoản không tồn tại với email hoặc số điện thoại: " + emailOrPhone);
        }

        return accountOpt.get();
    }

    @Override
    public AuthProfileResponse getMyProfile() {
        String email = SecurityUtil.getCurrentUserEmail();
        AuthAccount account = authAccountRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản"));

        var roles = account.getUserRoles().stream()
                .map(userRole -> userRole.getRole().getRoleKey())
                .toList();

        var user = account.getUser(); // mapping 1-1 với User
        if (user == null) {
            throw new ResourceNotFoundException("Không tìm thấy thông tin nhân viên");
        }

        return AuthProfileResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .email(account.getEmail())
                .signatureImage(user.getSignatureImage())
                .department(user.getDepartment() != null ? user.getDepartment().getName() : null)
                .position(user.getPosition() != null ? user.getPosition().getName() : null)
                .roles(roles)
                .build();
    }

}