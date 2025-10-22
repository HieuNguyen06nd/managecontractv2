package com.hieunguyen.ManageContract.dto.position;

import com.hieunguyen.ManageContract.common.constants.Status;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PositionRequest {

    @NotBlank(message = "Tên vị trí là bắt buộc")
    @Size(max = 255, message = "Tên vị trí tối đa 255 ký tự")
    private String name;

    @Size(max = 1000, message = "Mô tả tối đa 1000 ký tự")
    private String description;

    private Status status = Status.ACTIVE;

    private Long departmentId;
}
