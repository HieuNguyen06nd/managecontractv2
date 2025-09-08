package com.hieunguyen.ManageContract.service.impl;

import com.hieunguyen.ManageContract.entity.Contract;
import com.hieunguyen.ManageContract.entity.ContractVariableValue;
import com.hieunguyen.ManageContract.service.ContractFileService;
import org.docx4j.model.datastorage.migration.VariablePrepare;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ContractFileServiceImpl implements ContractFileService {

    public String generateContractFile(Contract contract) {
        try {
            // Load template file
            WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage
                    .load(new File(contract.getTemplate().getFilePath()));

            // Map biến từ DB
            Map<String, String> variables = contract.getVariableValues().stream()
                    .collect(Collectors.toMap(
                            ContractVariableValue::getVarName,
                            ContractVariableValue::getVarValue
                    ));

            // Replace ${varName} bằng giá trị
            VariablePrepare.prepare(wordMLPackage);
            wordMLPackage.getMainDocumentPart().variableReplace(variables);

            // Đường dẫn file mới
            String outputPath = "uploads/contracts/contract_" + contract.getId() + ".docx";

            // Lưu file
            wordMLPackage.save(new File(outputPath));

            return outputPath;

        } catch (Exception e) {
            throw new RuntimeException("Error generating contract file", e);
        }
    }
}
