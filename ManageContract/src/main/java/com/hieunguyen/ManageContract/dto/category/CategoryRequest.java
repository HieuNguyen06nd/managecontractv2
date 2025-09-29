package com.hieunguyen.ManageContract.dto.category;

import com.hieunguyen.ManageContract.common.constants.Status;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CategoryRequest {
    @NotBlank
    private String code;

    @NotBlank
    private String name;

    private String description;

    private Status status;
}
