package com.hieunguyen.ManageContract.service;

import com.hieunguyen.ManageContract.dto.authAccount.AuthProfileResponse;
import com.hieunguyen.ManageContract.dto.user.UserUpdateRequest;
import org.apache.coyote.BadRequestException;
import org.springframework.web.multipart.MultipartFile;


public interface UserService {
    AuthProfileResponse updateCurrentUser(UserUpdateRequest request) throws BadRequestException;
    AuthProfileResponse uploadSignatureBase64(String dataUrl) throws BadRequestException;
    AuthProfileResponse uploadSignature(MultipartFile signature) throws BadRequestException;
    AuthProfileResponse uploadAvatar(MultipartFile avatar) throws BadRequestException;
}
