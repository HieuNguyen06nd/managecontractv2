package com.hieunguyen.ManageContract.dto.department;

import com.hieunguyen.ManageContract.common.constants.Status;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DepartmentResponse {
    private Long id;
    private String name;
    private Integer level;
    private Long parentId;
    private String parentName;
    private Long leaderId;
    private String leaderName;
    private int employeeCount;
    private Status status;
}