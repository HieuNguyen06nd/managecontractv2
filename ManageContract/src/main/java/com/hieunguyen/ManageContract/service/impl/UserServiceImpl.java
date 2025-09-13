package com.hieunguyen.ManageContract.service.impl;

import com.hieunguyen.ManageContract.common.exception.ResourceNotFoundException;
import com.hieunguyen.ManageContract.dto.user.UserResponse;
import com.hieunguyen.ManageContract.dto.user.UserUpdateRequest;
import com.hieunguyen.ManageContract.entity.AuthAccount;
import com.hieunguyen.ManageContract.entity.User;
import com.hieunguyen.ManageContract.mapper.UserMapper;
import com.hieunguyen.ManageContract.repository.AuthAccountRepository;
import com.hieunguyen.ManageContract.repository.UserRepository;
import com.hieunguyen.ManageContract.security.jwt.SecurityUtil;
import com.hieunguyen.ManageContract.service.UserService;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.BadRequestException;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class UserServiceImpl implements UserService {

    private final AuthAccountRepository authAccountRepository;
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    public UserResponse updateCurrentUser(UserUpdateRequest request) throws BadRequestException {
        String email = SecurityUtil.getCurrentUserEmail();

        AuthAccount account = authAccountRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản"));


        User user = account.getUser();
        if (user == null) {
            throw new ResourceNotFoundException("Không tìm thấy thông tin người dùng");
        }

        userMapper.updateUserFromRequest(user, request);

        userRepository.save(user);
        return userMapper.toUserResponse(user, account);
    }
}