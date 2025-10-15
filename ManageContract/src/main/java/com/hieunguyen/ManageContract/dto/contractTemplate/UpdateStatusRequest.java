package com.hieunguyen.ManageContract.dto.contractTemplate;

import com.hieunguyen.ManageContract.common.constants.Status;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class UpdateStatusRequest {
    private Status status; // EXPECT: "ACTIVE" | "INACTIVE" | "LOCKED"
}