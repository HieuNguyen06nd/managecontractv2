package com.hieunguyen.ManageContract.dto.contract;

import lombok.Data;

import java.util.List;

@Data
public class CreateContractRequest {
    private Long templateId;
    private String title;
    private List<VariableValueRequest> variables;
    private Long flowId; // ID flow được chọn (nullable)
    private Boolean allowChangeFlow; // nếu muốn cho phép override flow
    private Long createdBy;

    @Data
    public static class VariableValueRequest {
        private String varName;
        private String varValue;
    }
}
