package com.hieunguyen.ManageContract.dto.authAccount;

import lombok.Data;

@Data
public class AuthAccountResponse {
    private Long id;
    private String email;
    private String phone;
    private Boolean emailVerified;
}