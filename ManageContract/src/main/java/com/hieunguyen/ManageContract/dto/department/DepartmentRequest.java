package com.hieunguyen.ManageContract.dto.department;

import com.hieunguyen.ManageContract.common.constants.Status;
import lombok.Data;

@Data
public class DepartmentRequest {
    private String name;
    private Integer level;
    private Long parentId;
    private Long leaderId;
    private Status status;
}