package com.hieunguyen.ManageContract.dto.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nimbusds.openid.connect.sdk.claims.Gender;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse {
    private Long userId;
    private String fullName;
    private String phone;
    private Gender gender;
    private String email;

    private List<String> roles;
}
