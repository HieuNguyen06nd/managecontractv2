package com.hieunguyen.ManageContract.dto.user;

import com.nimbusds.openid.connect.sdk.claims.Gender;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserUpdateRequest {

    private String fullName;
    private String phone;
    private Gender gender;
}
