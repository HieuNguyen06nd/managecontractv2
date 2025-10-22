package com.hieunguyen.ManageContract.dto.position;

import com.hieunguyen.ManageContract.common.constants.Status;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PositionResponse {
    private Long id;
    private String name;
    private String description;
    private Status status;

    private Long departmentId;
    private String departmentName;
}