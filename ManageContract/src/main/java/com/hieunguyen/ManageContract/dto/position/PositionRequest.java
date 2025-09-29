package com.hieunguyen.ManageContract.dto.position;

import com.hieunguyen.ManageContract.common.constants.Status;
import lombok.Data;

@Data
public class PositionRequest {
    private String name;
    private String description;
    private Status status;
}