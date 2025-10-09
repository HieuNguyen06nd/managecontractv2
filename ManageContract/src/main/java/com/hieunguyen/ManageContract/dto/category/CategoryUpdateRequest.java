package com.hieunguyen.ManageContract.dto.category;

import com.hieunguyen.ManageContract.common.constants.Status;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryUpdateRequest {

    @NotBlank(message = "Code không được để trống")
    @Size(min = 2, max = 100, message = "Code phải từ 2 đến 100 ký tự")
    private String code;

    @NotBlank(message = "Tên không được để trống")
    @Size(min = 2, max = 255, message = "Tên phải từ 2 đến 255 ký tự")
    private String name;

    @Size(max = 1000, message = "Mô tả không quá 1000 ký tự")
    private String description;

    private Status status;
}