package com.hieunguyen.ManageContract.service;

import com.hieunguyen.ManageContract.dto.authAccount.AuthProfileResponse;
import com.hieunguyen.ManageContract.entity.AuthAccount;

import java.util.List;

public interface AuthAccountService {
    AuthAccount findByEmailOrPhone(String emailOrPhone);
    AuthProfileResponse getMyProfile();

    List<AuthProfileResponse> getAll();
}
