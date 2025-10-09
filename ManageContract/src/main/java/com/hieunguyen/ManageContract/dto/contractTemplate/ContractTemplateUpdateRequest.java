package com.hieunguyen.ManageContract.dto.contractTemplate;

import lombok.Data;

@Data
public class ContractTemplateUpdateRequest {
    private String name;
    private String description;
    private Long categoryId;
}