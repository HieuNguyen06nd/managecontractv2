package com.hieunguyen.ManageContract.dto.authAccount;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nimbusds.openid.connect.sdk.claims.Gender;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthProfileResponse {
    private Long id;
    private String fullName;
    private String phone;
    private String email;
    private String signatureImage;
    private String department;
    private String position;
    private List<String> roles;
}
