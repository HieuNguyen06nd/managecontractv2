package com.hieunguyen.ManageContract.mapper;

import com.hieunguyen.ManageContract.common.constants.VariableType;
import com.hieunguyen.ManageContract.dto.authAccount.AuthAccountResponse;
import com.hieunguyen.ManageContract.dto.contractTemplate.ContractTemplateResponse;
import com.hieunguyen.ManageContract.dto.templateVariable.TemplateVariableResponse;
import com.hieunguyen.ManageContract.entity.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class ContractTemplateMapper {

    public static ContractTemplateResponse toResponse(ContractTemplate template) {
        if (template == null) return null;

        ContractTemplateResponse dto = new ContractTemplateResponse();
        dto.setId(template.getId());
        dto.setName(template.getName());
        dto.setDescription(template.getDescription());
        dto.setFilePath(template.getFilePath());
        dto.setStatus(template.getStatus());

        // Map createdBy từ User
        if (template.getCreatedBy() != null) {
            Employee employee = template.getCreatedBy();
            AuthAccount account = employee.getAccount();
            if (account != null) {
                AuthAccountResponse dtoAccount = new AuthAccountResponse();
                dtoAccount.setId(account.getId());
                dtoAccount.setEmail(account.getEmail());
                dtoAccount.setPhone(account.getPhone());
                dtoAccount.setEmailVerified(account.isEmailVerified());
                dto.setCreatedBy(dtoAccount);
            }
        }

        // Map category
        if (template.getCategory() != null) {
            dto.setCategoryId(template.getCategory().getId());
            dto.setCategoryCode(template.getCategory().getCode());
            dto.setCategoryName(template.getCategory().getName());
            dto.setCategoryStatus(template.getCategory().getStatus());
        }

        // Map default flow info
        if (template.getDefaultFlow() != null) {
            dto.setDefaultFlowId(template.getDefaultFlow().getId());
            dto.setDefaultFlowName(template.getDefaultFlow().getName());
        }

        // QUAN TRỌNG: Hợp nhất cả biến thường và biến TABLE
        dto.setVariables(mergeAllVariables(template));

        return dto;
    }

    /**
     * Hợp nhất tất cả biến (thường + TABLE) vào một danh sách
     */
    private static List<TemplateVariableResponse> mergeAllVariables(ContractTemplate template) {
        List<TemplateVariableResponse> allVariables = new ArrayList<>();

        // Map biến thường
        if (template.getVariables() != null) {
            List<TemplateVariableResponse> regularVars = template.getVariables().stream()
                    .map(TemplateVariableMapper::toResponse)
                    .collect(Collectors.toList());
            allVariables.addAll(regularVars);
        }

        // Map biến TABLE
        if (template.getTableVariables() != null) {
            List<TemplateVariableResponse> tableVars = template.getTableVariables().stream()
                    .map(ContractTemplateMapper::tableVariableToResponse)
                    .collect(Collectors.toList());
            allVariables.addAll(tableVars);
        }

        // Sắp xếp theo orderIndex
        allVariables.sort(Comparator.comparingInt(TemplateVariableResponse::getOrderIndex));

        log.debug("Merged {} total variables ({} regular + {} table) for template {}",
                allVariables.size(),
                template.getVariables() != null ? template.getVariables().size() : 0,
                template.getTableVariables() != null ? template.getTableVariables().size() : 0,
                template.getId());

        return allVariables;
    }

    /**
     * Chuyển TemplateTableVariable thành TemplateVariableResponse
     */
    private static TemplateVariableResponse tableVariableToResponse(TemplateTableVariable tableVariable) {
        TemplateVariableResponse response = new TemplateVariableResponse();
        response.setId(tableVariable.getId());
        response.setVarName("table_" + tableVariable.getTableName()); // Giữ format table_
        response.setVarType(VariableType.TABLE);
        response.setName(tableVariable.getDisplayName() != null ?
                tableVariable.getDisplayName() : tableVariable.getTableName());
        response.setRequired(true); // Biến table luôn required
        response.setOrderIndex(tableVariable.getOrderIndex() != null ?
                tableVariable.getOrderIndex() : 0);
        response.setDefaultValue("");
        response.setAllowedValues(new ArrayList<>());

        // Xây dựng config cho biến TABLE
        Map<String, Object> config = buildTableConfig(tableVariable);
        response.setConfig(config);

        log.debug("Converted table variable: {} with {} columns",
                tableVariable.getTableName(),
                tableVariable.getColumns() != null ? tableVariable.getColumns().size() : 0);

        return response;
    }

    /**
     * Xây dựng config cho biến TABLE
     */
    private static Map<String, Object> buildTableConfig(TemplateTableVariable tableVariable) {
        Map<String, Object> config = new HashMap<>();

        // Thông tin cơ bản của bảng
        config.put("tableName", tableVariable.getTableName());
        config.put("minRows", tableVariable.getMinRows() != null ? tableVariable.getMinRows() : 1);
        config.put("maxRows", tableVariable.getMaxRows() != null ? tableVariable.getMaxRows() : 10);
        config.put("editable", tableVariable.getEditable() != null ? tableVariable.getEditable() : true);

        // Columns configuration
        List<Map<String, Object>> columns = new ArrayList<>();
        if (tableVariable.getColumns() != null) {
            for (TableColumn column : tableVariable.getColumns()) {
                Map<String, Object> columnConfig = new HashMap<>();
                columnConfig.put("name", column.getColumnName());
                columnConfig.put("displayName", column.getDisplayName());
                columnConfig.put("type", column.getColumnType() != null ?
                        column.getColumnType().name() : VariableType.STRING.name());
                columnConfig.put("required", column.getRequired() != null ? column.getRequired() : true);
                columnConfig.put("order", column.getColumnOrder() != null ? column.getColumnOrder() : 0);
                columns.add(columnConfig);
            }
        } else {
            // Fallback: tạo columns mặc định nếu không có
            columns.add(createDefaultColumn("column_1", "Cột 1", 0));
            columns.add(createDefaultColumn("column_2", "Cột 2", 1));
        }
        config.put("columns", columns);

        return config;
    }

    /**
     * Tạo column mặc định
     */
    private static Map<String, Object> createDefaultColumn(String name, String displayName, int order) {
        Map<String, Object> column = new HashMap<>();
        column.put("name", name);
        column.put("displayName", displayName);
        column.put("type", VariableType.STRING.name());
        column.put("required", true);
        column.put("order", order);
        return column;
    }

    /**
     * Map đơn giản cho TemplateVariable (giữ lại cho tương thích)
     */
    private static TemplateVariableResponse toVariableResponse(TemplateVariable variable) {
        return TemplateVariableMapper.toResponse(variable);
    }
}