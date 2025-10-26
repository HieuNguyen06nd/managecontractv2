package com.hieunguyen.ManageContract.dto.department;

import com.hieunguyen.ManageContract.common.constants.Status;
import com.hieunguyen.ManageContract.dto.position.PositionResponse;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

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

    @Builder.Default
    private List<PositionResponse> positions = new ArrayList<>();
}