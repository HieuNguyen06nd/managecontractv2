package com.hieunguyen.ManageContract.dto.authAccount;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordRequest {
    @Email
    private String email;

    @NotBlank
    private String newPassword;
}
