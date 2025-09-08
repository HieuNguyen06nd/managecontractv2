package com.hieunguyen.ManageContract.dto.contract;

import lombok.Data;

import java.util.List;

@Data
public class ContractResponse {
    private Long id;
    private String contractNumber;
    private String title;
    private String status;
    private String filePath; // đường dẫn file nếu có
    private String templateName;
    private List<VariableValueResponse> variables;

    @Data
    public static class VariableValueResponse {
        private String varName;
        private String varValue;
    }

}
