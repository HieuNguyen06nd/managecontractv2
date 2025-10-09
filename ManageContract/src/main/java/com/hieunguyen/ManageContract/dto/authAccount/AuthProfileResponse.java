package com.hieunguyen.ManageContract.dto.authAccount;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hieunguyen.ManageContract.common.constants.StatusUser;
import com.hieunguyen.ManageContract.dto.role.RoleResponse;
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
    private String avatarImage;
    private String department;
    private String position;
    private Gender gender;
    private StatusUser status;
    private List<RoleResponse> roles;
}
