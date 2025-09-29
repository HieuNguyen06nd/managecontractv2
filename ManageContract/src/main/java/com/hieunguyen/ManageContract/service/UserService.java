package com.hieunguyen.ManageContract.service;

import com.hieunguyen.ManageContract.dto.authAccount.AuthProfileResponse;
import com.hieunguyen.ManageContract.dto.user.UserUpdateRequest;
import org.apache.coyote.BadRequestException;


public interface UserService {
    AuthProfileResponse updateCurrentUser(UserUpdateRequest request) throws BadRequestException;

}
