package com.hieunguyen.ManageContract.service;

import com.hieunguyen.ManageContract.dto.user.UserResponse;
import com.hieunguyen.ManageContract.dto.user.UserUpdateRequest;
import org.apache.coyote.BadRequestException;

public interface UserService {
    UserResponse updateCurrentUser(UserUpdateRequest request) throws BadRequestException;
}
