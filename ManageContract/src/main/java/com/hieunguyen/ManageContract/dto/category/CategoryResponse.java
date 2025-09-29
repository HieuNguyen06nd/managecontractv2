package com.hieunguyen.ManageContract.dto.category;

import com.hieunguyen.ManageContract.common.constants.Status;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class CategoryResponse {
    private Long id;
    private String code;
    private String name;
    private String description;
    private Status status;
    private Instant createdAt;
    private Instant updatedAt;
}
