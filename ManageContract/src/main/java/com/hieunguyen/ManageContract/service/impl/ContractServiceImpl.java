package com.hieunguyen.ManageContract.service.impl;

import com.hieunguyen.ManageContract.common.constants.ApprovalStatus;
import com.hieunguyen.ManageContract.common.constants.ApproverType;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContractServiceImpl implements ContractService {
    private final ContractRepository contractRepository;
    private final ContractVariableValueRepository variableValueRepository;
    private final ContractTemplateRepository templateRepository;
    private final ApprovalFlowRepository flowRepository;
    private final ContractApprovalRepository contractApprovalRepository;
    private final UserRepository userRepository;
    private final PositionRepository positionRepository;
    private final DepartmentRepository departmentRepository;
    private final ApprovalFlowRepository approvalFlowRepository;

    @Transactional
    @Override
    public ContractResponse createContract(CreateContractRequest request) {
        // Lấy thông tin template hợp đồng từ request
        ContractTemplate template = templateRepository.findById(request.getTemplateId())
                .orElseThrow(() -> new RuntimeException("Template not found"));

        // Lấy thông tin người tạo hợp đồng từ email đăng nhập
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Employee createdBy = userRepository.findByAccount_Email(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nhân viên với email: " + email));

        // Tạo hợp đồng mới
        Contract contract = new Contract();
        contract.setTitle(request.getTitle());
        contract.setContractNumber("HD-" + System.currentTimeMillis()); // Mã hợp đồng tự động
        contract.setTemplate(template);
        contract.setCreatedBy(createdBy);
        contract.setStatus(ContractStatus.DRAFT); // Trạng thái mặc định là DRAFT

        // Lưu hợp đồng vào cơ sở dữ liệu
        Contract savedContract = contractRepository.save(contract);

        // Lưu các biến hợp đồng
        Contract finalSavedContract = savedContract;
        List<ContractVariableValue> values = request.getVariables().stream()
                .map(v -> {
                    // Giữ vững rằng các biến này không thay đổi trong lambda
                    final String varName = v.getVarName();  // Đảm bảo là final
                    final String varValue = v.getVarValue(); // Đảm bảo là final

                    ContractVariableValue cv = new ContractVariableValue();
                    cv.setContract(finalSavedContract); // Liên kết với hợp đồng
                    cv.setVarName(varName);
                    cv.setVarValue(varValue);
                    return cv;
                })
                .collect(Collectors.toList());

        variableValueRepository.saveAll(values);
        savedContract.setVariableValues(values);

        // Kiểm tra lựa chọn luồng ký (luồng mặc định, tạo mới, hoặc có sẵn)
        ApprovalFlow flow = null;
        if ("default".equals(request.getFlowOption()) && template.getDefaultFlow() != null) {
            flow = template.getDefaultFlow();
            contract.setFlow(flow);
        } else if ("new".equals(request.getFlowOption())) {
            // Tạo luồng ký mới theo thông tin từ user
            flow = new ApprovalFlow();
            flow.setName("Luồng ký cho hợp đồng " + contract.getContractNumber());
            flow.setDescription("Luồng ký được tạo thủ công cho hợp đồng");

            // Thêm các bước ký vào luồng (dựa trên thông tin người ký từ request)
            List<ApprovalStep> steps = createApprovalFlow(request);
            flow.setSteps(new LinkedHashSet<>(steps)); // Gán các bước ký vào luồng mới

            // Lưu luồng ký mới vào cơ sở dữ liệu
            flow = approvalFlowRepository.save(flow);
            contract.setFlow(flow);  // Gán flow vào hợp đồng
        } else if ("existing".equals(request.getFlowOption()) && request.getExistingFlowId() != null) {
            // Chọn luồng ký có sẵn từ database
            flow = approvalFlowRepository.findById(request.getExistingFlowId())
                    .orElseThrow(() -> new RuntimeException("Approval flow not found"));
            contract.setFlow(flow);
        }

        // Lưu hợp đồng đã có luồng ký vào cơ sở dữ liệu
        savedContract = contractRepository.save(contract);

        // Trả về hợp đồng đã lưu dưới dạng DTO
        return ContractMapper.toResponse(savedContract);
    }

    // Hàm tạo các bước ký cho luồng ký mới
    private List<ApprovalStep> createApprovalFlow(CreateContractRequest request) {
        // Tạo luồng ký mới
        ApprovalFlow newFlow = new ApprovalFlow();
        newFlow.setName(request.getFlowName());
        newFlow.setDescription(request.getFlowDescription());

        // Tạo các bước ký
        Set<ApprovalStep> steps = new LinkedHashSet<>();
        for (CreateContractRequest.SignStepRequest stepRequest : request.getSignSteps()) {
            ApprovalStep step = new ApprovalStep();
            step.setStepOrder(steps.size() + 1);  // Thứ tự bước ký
            step.setRequired(stepRequest.getRequired());
            step.setApproverType(stepRequest.getApproverType()); // USER hoặc POSITION

            if (ApproverType.USER.equals(stepRequest.getApproverType())) {
                // Nếu là user, lấy thông tin người duyệt
                Employee approver = userRepository.findById(stepRequest.getEmployeeId())
                        .orElseThrow(() -> new RuntimeException("User not found"));
                step.setEmployee(approver);
            } else if (ApproverType.POSITION.equals(stepRequest.getApproverType())) {
                // Nếu là vị trí, lấy thông tin phòng ban và chức vụ
                Position position = positionRepository.findById(stepRequest.getPositionId())
                        .orElseThrow(() -> new RuntimeException("Position not found"));
                Department department = departmentRepository.findById(stepRequest.getDepartmentId())
                        .orElseThrow(() -> new RuntimeException("Department not found"));
                step.setPosition(position);
                step.setDepartment(department);
            }

            step.setAction(stepRequest.getAction()); // APPROVE_ONLY, SIGN_ONLY, SIGN_THEN_APPROVE
            step.setSignaturePlaceholder(stepRequest.getSignaturePlaceholder());
            step.setIsFinalStep(stepRequest.getIsFinalStep()); // Bước cuối cùng hay không
            step.setFlow(newFlow); // Gán flow cho bước ký
            steps.add(step);
        }

        newFlow.setSteps(steps); // Gán các bước vào luồng
        newFlow = approvalFlowRepository.save(newFlow); // Lưu luồng ký mới vào cơ sở dữ liệu

        return new ArrayList<>(steps); // Trả về danh sách các bước ký
    }


    @Override
    @Transactional
    public ContractResponse getById(Long id) {
        // Nên fetch kèm variableValues để tránh Lazy
        Contract contract = contractRepository.findWithVarsById(id)
                .orElseThrow(() -> new RuntimeException("Contract not found"));
        return ContractMapper.toResponse(contract);

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


    @Override
    public List<ContractResponse> getMyContracts(ContractStatus status) {
        String email = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getName();

        List<Contract> list = (status == null)
                ? contractRepository.findByCreatedBy_Account_Email(email)
                : contractRepository.findByCreatedBy_Account_EmailAndStatus(email, status);

        return list.stream().map(ContractMapper::toResponse).toList();
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

    @Transactional
    @Override
    public ContractResponse updateContract(Long contractId, CreateContractRequest request) {
        // Lấy hợp đồng từ database
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        if (!contract.getStatus().equals(ContractStatus.DRAFT) &&
                !contract.getStatus().equals(ContractStatus.PENDING_APPROVAL)) {
            throw new RuntimeException("Không thể chỉnh sửa hợp đồng trong trạng thái này.");
        }

        contract.setTitle(request.getTitle());

        // Cập nhật các biến hợp đồng
        List<ContractVariableValue> updatedValues = new ArrayList<>();
        for (CreateContractRequest.VariableValueRequest variable : request.getVariables()) {
            ContractVariableValue cv = variableValueRepository
                    .findByContract_IdAndVarName(contract.getId(), variable.getVarName())
                    .orElse(new ContractVariableValue());
            cv.setContract(contract);
            cv.setVarName(variable.getVarName());
            cv.setVarValue(variable.getVarValue());
            updatedValues.add(cv);
        }
        contract.getVariableValues().clear();  // Clear the old ones
        contract.getVariableValues().addAll(updatedValues); // Add new values

        // Lưu lại các biến hợp đồng đã cập nhật
        variableValueRepository.saveAll(updatedValues);

        // Lưu lại hợp đồng với các thay đổi
        contractRepository.save(contract);

        // Trả về DTO hợp đồng đã được cập nhật
        return ContractMapper.toResponse(contract);
    }


    @Transactional
    public void changeApprover(Long contractId, Long stepId, Long newApproverId, boolean isUserApprover) {
        // Tìm hợp đồng
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        // Tìm bước phê duyệt cần thay đổi người ký trong bảng contract_approvals
        ContractApproval contractApproval = contractApprovalRepository.findByContract_IdAndStep_Id(contractId, stepId)
                .orElseThrow(() -> new RuntimeException("Approval step not found"));

        // Kiểm tra trạng thái của người ký, nếu đã phê duyệt hoặc bị từ chối, không cho phép thay đổi
        if (contractApproval.getStatus() == ApprovalStatus.APPROVED || contractApproval.getStatus() == ApprovalStatus.REJECTED) {
            throw new RuntimeException("Không thể thay đổi người ký vì bước phê duyệt này đã được quyết định.");
        }

        // Nếu trạng thái là PENDING, bạn có thể thay đổi người ký
        if (isUserApprover) {
            // Thay đổi từ Position sang User
            if (contractApproval.getPosition() != null) {
                contractApproval.setPosition(null); // Xóa Position cũ
                Employee newUser = userRepository.findById(newApproverId)
                        .orElseThrow(() -> new RuntimeException("User not found"));
                contractApproval.setApprover(newUser); // Gán User mới
                contractApproval.setDepartment(null); // Không cần department nữa
            } else {
                throw new RuntimeException("Current approver is not a Position, cannot change to User.");
            }
        } else {
            // Thay đổi từ User sang Position
            if (contractApproval.getApprover() != null) {
                contractApproval.setApprover(null); // Xóa User cũ

                // Tìm Position và Department mới
                Position newPosition = positionRepository.findById(newApproverId)
                        .orElseThrow(() -> new RuntimeException("Position not found"));
                Department newDepartment = departmentRepository.findById(newApproverId) // Tìm department tương ứng với Position
                        .orElseThrow(() -> new RuntimeException("Department not found"));

                contractApproval.setPosition(newPosition); // Gán Position mới
                contractApproval.setDepartment(newDepartment); // Gán department cho Position
            } else {
                throw new RuntimeException("Current approver is not a User, cannot change to Position.");
            }
        }

        // Lưu lại thay đổi trong ContractApproval
        contractApprovalRepository.save(contractApproval);
    }

    @Transactional
    @Override
    public void cancelContract(Long contractId) {
        // Lấy hợp đồng từ database
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        if (contract.getStatus() == ContractStatus.PENDING_APPROVAL) {
            List<ContractApproval> contractApprovals = contractApprovalRepository.findByContract(contract);
            for (ContractApproval approval : contractApprovals) {
                approval.setStatus(ApprovalStatus.CANCELLED);
                contractApprovalRepository.save(approval);
            }
        } else {
            throw new RuntimeException("Không thể hủy hợp đồng vì hợp đồng không phải trong trạng thái trình ký.");
        }

        // Cập nhật trạng thái hợp đồng thành "CANCELLED"
        contract.setStatus(ContractStatus.CANCELLED);

        // Lưu lại hợp đồng với trạng thái đã cập nhật
        contractRepository.save(contract);
    }


    @Transactional
    @Override
    public void deleteContract(Long contractId) {
        // Lấy hợp đồng từ database
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        // Kiểm tra trạng thái của hợp đồng, chỉ cho phép xóa hợp đồng khi hợp đồng ở trạng thái DRAFT
        if (!contract.getStatus().equals(ContractStatus.DRAFT)) {
            throw new RuntimeException("Không thể xóa hợp đồng vì hợp đồng không ở trạng thái DRAFT.");
        }

        // Xóa hợp đồng
        contractRepository.delete(contract);
    }


}