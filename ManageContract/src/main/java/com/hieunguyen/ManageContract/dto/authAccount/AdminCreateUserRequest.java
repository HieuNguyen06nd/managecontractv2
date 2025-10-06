package com.hieunguyen.ManageContract.dto.authAccount;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class AdminCreateUserRequest {
    @NotBlank
    @Email
    private String email;
    private String phone;
    private String fullName;
    private List<String> roleKeys;    // optional -> mặc định EMPLOYEE
    private Long departmentId;        // optional
    private Long positionId;          // optional
}

