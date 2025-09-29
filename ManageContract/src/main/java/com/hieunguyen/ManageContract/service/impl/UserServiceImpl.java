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
import com.hieunguyen.ManageContract.service.UserService;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.BadRequestException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class UserServiceImpl implements UserService {

    private final AuthAccountRepository authAccountRepository;
    private final UserRepository userRepository;
    private final UserMapper userMapper;

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

}