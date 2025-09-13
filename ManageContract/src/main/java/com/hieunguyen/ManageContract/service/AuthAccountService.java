package com.hieunguyen.ManageContract.service;

import com.hieunguyen.ManageContract.dto.authAccount.AuthProfileResponse;
import com.hieunguyen.ManageContract.entity.AuthAccount;

public interface AuthAccountService {
    AuthAccount findByEmailOrPhone(String emailOrPhone);
    AuthProfileResponse getMyProfile();
}
