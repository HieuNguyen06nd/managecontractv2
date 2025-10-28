package com.hieunguyen.ManageContract.service.impl;

import com.hieunguyen.ManageContract.common.exception.ResourceNotFoundException;
import com.hieunguyen.ManageContract.dto.authAccount.AuthProfileResponse;
import com.hieunguyen.ManageContract.dto.user.UserUpdateRequest;
import com.hieunguyen.ManageContract.entity.AuthAccount;
import com.hieunguyen.ManageContract.entity.Department;
import com.hieunguyen.ManageContract.entity.Employee;
import com.hieunguyen.ManageContract.mapper.UserMapper;
import com.hieunguyen.ManageContract.repository.AuthAccountRepository;
import com.hieunguyen.ManageContract.repository.DepartmentRepository;
import com.hieunguyen.ManageContract.repository.PositionRepository;
import com.hieunguyen.ManageContract.repository.UserRepository;
import com.hieunguyen.ManageContract.security.jwt.SecurityUtil;
import com.hieunguyen.ManageContract.service.FileUploadService;
import com.hieunguyen.ManageContract.service.UserService;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@RequiredArgsConstructor
@Service
public class UserServiceImpl implements UserService {

    private final AuthAccountRepository authAccountRepository;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final FileUploadService fileUploadService;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;

    @Override
    public AuthProfileResponse updateCurrentUser(UserUpdateRequest request) throws BadRequestException {
        String email = SecurityUtil.getCurrentUserEmail();

        AuthAccount account = authAccountRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản"));

        Employee employee = account.getEmployee();
        if (employee == null) {
            throw new ResourceNotFoundException("Không tìm thấy thông tin người dùng");
        }

        userMapper.updateUserFromRequest(employee, request);
        userRepository.save(employee);
        return userMapper.toUserResponse(employee, account);
    }

    @Override
    public AuthProfileResponse uploadSignature(MultipartFile signature) throws BadRequestException {
        if (signature == null || signature.isEmpty()) throw new BadRequestException("Chọn ảnh chữ ký");
        try {
            Employee e = currentEmployee();
            String relPath = fileUploadService.saveMultipart(e.getId(), signature, "signatures");
            e.setSignatureImage(relPath);
            e.setSignatureUpdatedAt(LocalDateTime.now());
            userRepository.save(e);
            return userMapper.toUserResponse(e, e.getAccount());
        } catch (Exception ex) {
            throw new BadRequestException("Lỗi upload chữ ký: " + ex.getMessage());
        }
    }

    @Override
    public AuthProfileResponse uploadSignatureBase64(String dataUrl) throws BadRequestException {
        if (dataUrl == null || dataUrl.isBlank()) throw new BadRequestException("Thiếu dữ liệu base64");
        try {
            Employee e = currentEmployee();
            String relPath = fileUploadService.saveBase64(e.getId(), dataUrl, "signatures", "png");
            e.setSignatureImage(relPath);
            e.setSignatureUpdatedAt(LocalDateTime.now());
            userRepository.save(e);
            return userMapper.toUserResponse(e, e.getAccount());
        } catch (Exception ex) {
            throw new BadRequestException("Lỗi upload chữ ký (base64): " + ex.getMessage());
        }
    }

    @Override
    public AuthProfileResponse uploadAvatar(MultipartFile avatar) throws BadRequestException {
        if (avatar == null || avatar.isEmpty()) throw new BadRequestException("Chọn ảnh avatar");
        try {
            Employee e = currentEmployee();
            String relPath = fileUploadService.saveMultipart(e.getId(), avatar, "avatars");
            e.setAvatarImage(relPath);
            userRepository.save(e);
            return userMapper.toUserResponse(e, e.getAccount());
        } catch (Exception ex) {
            throw new BadRequestException("Lỗi upload avatar: " + ex.getMessage());
        }
    }

    @Override
    @Transactional
    public AuthProfileResponse updateUserByAdmin(Long userId, UserUpdateRequest request) throws BadRequestException {
        if (request == null) throw new BadRequestException("Thiếu dữ liệu cập nhật");

        Employee employee = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với id: " + userId));

        AuthAccount account = employee.getAccount();
        if (account == null) throw new ResourceNotFoundException("Tài khoản đăng nhập của người dùng không tồn tại");

        // Map các field cơ bản vào Employee (fullName, phone, gender, ...)
        userMapper.updateUserFromRequest(employee, request);

        // ====== Update phòng ban & vị trí với ràng buộc ======
        Department newDept = null;
        boolean deptChanged = false;

        // 1) Nếu client gửi departmentId -> lấy và set
        if (request.getDepartmentId() != null) {
            newDept = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phòng ban"));
            employee.setDepartment(newDept);
            deptChanged = true;
        }

        // 2) Nếu client gửi positionId -> validate vị trí thuộc phòng ban hiệu lực
        if (request.getPositionId() != null) {
            var newPos = positionRepository.findById(request.getPositionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vị trí"));

            Department effectiveDept = (newDept != null) ? newDept : employee.getDepartment();
            if (effectiveDept == null) {
                throw new BadRequestException("Vui lòng chọn phòng ban trước khi chọn vị trí");
            }
            if (newPos.getDepartment() == null || !newPos.getDepartment().getId().equals(effectiveDept.getId())) {
                throw new BadRequestException("Vị trí không thuộc phòng ban đã chọn");
            }
            employee.setPosition(newPos);
        }

        // 3) Đã đổi phòng ban nhưng không gửi positionId mới: kiểm tra vị trí hiện tại còn hợp lệ
        if (deptChanged && employee.getPosition() != null) {
            var currentPos = employee.getPosition();
            if (currentPos.getDepartment() == null ||
                    !currentPos.getDepartment().getId().equals(employee.getDepartment().getId())) {
                throw new BadRequestException("Phòng ban đã thay đổi nhưng vị trí hiện tại không thuộc phòng ban này. " +
                        "Vui lòng chọn vị trí hợp lệ.");
                // Hoặc: employee.setPosition(null); // nếu muốn auto clear
            }
        }
        // ====== END ======

        userRepository.save(employee);
        return userMapper.toUserResponse(employee, account);
    }

    private Employee currentEmployee() {
        String email = SecurityUtil.getCurrentUserEmail();
        return userRepository.findByAccount_Email(email)
                .orElseThrow(() -> new RuntimeException("Người dùng không tồn tại"));
    }
}
