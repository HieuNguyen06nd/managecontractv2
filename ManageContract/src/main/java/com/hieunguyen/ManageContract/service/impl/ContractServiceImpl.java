package com.hieunguyen.ManageContract.service.impl;

import com.hieunguyen.ManageContract.common.constants.ApprovalStatus;
import com.hieunguyen.ManageContract.common.constants.ContractStatus;
import com.hieunguyen.ManageContract.common.constants.DocxToHtmlConverter;
import com.hieunguyen.ManageContract.dto.contract.ContractResponse;
import com.hieunguyen.ManageContract.dto.contract.CreateContractRequest;
import com.hieunguyen.ManageContract.entity.*;
import com.hieunguyen.ManageContract.mapper.ContractMapper;
import com.hieunguyen.ManageContract.repository.*;
import com.hieunguyen.ManageContract.service.ContractService;
import jakarta.transaction.Transactional;
import jakarta.xml.bind.JAXBElement;
import lombok.RequiredArgsConstructor;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.ContentAccessor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.docx4j.wml.Text;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ContractServiceImpl implements ContractService {
    private final ContractRepository contractRepository;
    private final ContractVariableValueRepository variableValueRepository;
    private final ContractTemplateRepository templateRepository;
    private final ApprovalFlowRepository flowRepository;
    private final ContractApprovalRepository contractApprovalRepository;
    private final UserRepository userRepository;

    @Transactional
    @Override
    public ContractResponse createContract(CreateContractRequest request) {
        // [PHẦN CODE NÀY KHÔNG THAY ĐỔI]
        ContractTemplate template = templateRepository.findById(request.getTemplateId())
                .orElseThrow(() -> new RuntimeException("Template not found"));

        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        Employee createdBy = userRepository.findByAccount_Email(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nhân viên với email: " + email));

        Contract contract = new Contract();
        contract.setTitle(request.getTitle());
        contract.setContractNumber("HD-" + System.currentTimeMillis());
        contract.setTemplate(template);
        contract.setCreatedBy(createdBy);
        contract.setStatus(ContractStatus.DRAFT);

        Contract saved = contractRepository.save(contract);

        // Lưu biến hợp đồng
        List<ContractVariableValue> values = request.getVariables().stream()
                .map(v -> {
                    ContractVariableValue cv = new ContractVariableValue();
                    cv.setContract(saved);
                    cv.setVarName(v.getVarName());
                    cv.setVarValue(v.getVarValue());
                    return cv;
                })
                .toList();

        variableValueRepository.saveAll(values);
        saved.setVariableValues(values);

        // Dùng mapper để trả ra DTO
        return ContractMapper.toResponse(saved);
    }

    @Transactional
    @Override
    public String previewContract(Long contractId) {
        // Hỗ trợ cả DOCX và HTML, thay biến theo list ContractVariableValue
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        ContractTemplate template = contract.getTemplate();
        if (template == null || template.getFilePath() == null) {
            throw new RuntimeException("Template file not found");
        }

        try {
            Path path = Path.of(template.getFilePath());
            String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
            List<ContractVariableValue> values = variableValueRepository.findByContract_Id(contractId);

            if (fileName.endsWith(".docx")) {
                // DOCX -> thay biến -> render HTML
                WordprocessingMLPackage pkg = WordprocessingMLPackage.load(new File(template.getFilePath()));
                replaceDocxVariables(pkg, values);
                return DocxToHtmlConverter.convertToHtml(pkg);
            } else {
                // HTML/TXT -> đọc nội dung -> thay biến (hỗ trợ cả ${var} và {{var}})
                String templateContent = Files.readString(path, StandardCharsets.UTF_8);
                return replaceTextVariables(templateContent, values);
            }

        } catch (Exception e) {
            throw new RuntimeException("Error while preview contract: " + e.getMessage(), e);
        }
    }

    @Transactional
    @Override
    public String previewTemplate(CreateContractRequest request) {
        ContractTemplate template = templateRepository.findById(request.getTemplateId())
                .orElseThrow(() -> new RuntimeException("Template not found"));

        if (template.getFilePath() == null) {
            throw new RuntimeException("Template file path not found");
        }

        try {
            Path path = Path.of(template.getFilePath());
            String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);

            // Map request variables thành ContractVariableValue “tạm” để tái dùng hàm thay biến
            List<ContractVariableValue> values = request.getVariables().stream().map(v -> {
                ContractVariableValue cv = new ContractVariableValue();
                cv.setVarName(v.getVarName());
                cv.setVarValue(v.getVarValue());
                return cv;
            }).toList();

            if (fileName.endsWith(".docx")) {
                WordprocessingMLPackage pkg = WordprocessingMLPackage.load(new File(template.getFilePath()));
                replaceDocxVariables(pkg, values);
                return DocxToHtmlConverter.convertToHtml(pkg);
            } else {
                String templateContent = Files.readString(path, StandardCharsets.UTF_8);
                return replaceTextVariables(templateContent, values);
            }

        } catch (Exception e) {
            throw new RuntimeException("Error while previewing template: " + e.getMessage(), e);
        }
    }


    /** Thay biến trong DOCX: hỗ trợ cú pháp ${var} và {{var}} */
    private void replaceDocxVariables(WordprocessingMLPackage pkg, List<ContractVariableValue> values) {
        Map<String, String> map = new HashMap<>();
        for (ContractVariableValue v : values) {
            map.put(v.getVarName(), v.getVarValue() == null ? "" : v.getVarValue());
        }

        List<Text> textNodes = getAllTextElements(pkg.getMainDocumentPart());
        for (Text t : textNodes) {
            String s = t.getValue();
            if (s == null || s.isEmpty()) continue;

            for (Map.Entry<String, String> e : map.entrySet()) {
                String key = e.getKey();
                String val = e.getValue();

                // ${var}
                s = s.replace("${" + key + "}", val);
                // {{var}}
                s = s.replace("{{" + key + "}}", val);
            }
            t.setValue(s);
        }
    }

    /** Thay biến trong HTML/TXT: hỗ trợ cả ${var} và {{var}} */
    private String replaceTextVariables(String content, List<ContractVariableValue> values) {
        if (content == null) return "";
        String result = content;
        for (ContractVariableValue v : values) {
            String key = v.getVarName();
            String val = v.getVarValue() == null ? "" : v.getVarValue();
            result = result.replace("${" + key + "}", val);
            result = result.replace("{{" + key + "}}", val);
        }
        return result;
    }

    private List<Text> getAllTextElements(Object obj) {
        List<Text> texts = new ArrayList<>();
        if (obj == null) return texts;

        if (obj instanceof JAXBElement) {
            obj = ((JAXBElement<?>) obj).getValue();
        }

        if (obj instanceof Text) {
            texts.add((Text) obj);
        } else if (obj instanceof ContentAccessor) {
            List<?> children = ((ContentAccessor) obj).getContent();
            for (Object child : children) {
                texts.addAll(getAllTextElements(child));
            }
        }
        return texts;
    }

}