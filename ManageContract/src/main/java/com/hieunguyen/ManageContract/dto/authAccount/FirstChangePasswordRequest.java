package com.hieunguyen.ManageContract.dto.authAccount;
import jakarta.validation.constraints.NotBlank;
public record FirstChangePasswordRequest(@NotBlank String newPassword) {}
