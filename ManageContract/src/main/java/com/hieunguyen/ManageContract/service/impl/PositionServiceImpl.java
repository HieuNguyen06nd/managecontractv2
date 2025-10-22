package com.hieunguyen.ManageContract.service.impl;

import com.hieunguyen.ManageContract.dto.position.PositionRequest;
import com.hieunguyen.ManageContract.dto.position.PositionResponse;
import com.hieunguyen.ManageContract.entity.Department;
import com.hieunguyen.ManageContract.entity.Position;
import com.hieunguyen.ManageContract.repository.DepartmentRepository;
import com.hieunguyen.ManageContract.repository.PositionRepository;
import com.hieunguyen.ManageContract.service.PositionService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PositionServiceImpl implements PositionService {

    private final PositionRepository positionRepository;
    private final DepartmentRepository departmentRepository;

    // --------- READ ALL ----------
    @Override
    @Transactional(readOnly = true)
    public List<PositionResponse> getAllPositions() {
        return positionRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // --------- READ ONE ----------
    @Override
    @Transactional(readOnly = true)
    public PositionResponse getPositionById(Long id) {
        Position p = positionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Position not found: " + id));
        return toResponse(p);
    }

    // --------- CREATE ----------
    @Override
    public PositionResponse createPosition(PositionRequest request) {
        Position p = new Position();
        p.setName(request.getName());
        p.setDescription(request.getDescription());
        p.setStatus(request.getStatus());

        if (request.getDepartmentId() != null) {
            Department d = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new EntityNotFoundException("Department not found: " + request.getDepartmentId()));
            p.setDepartment(d);
        }

        p = positionRepository.save(p);
        return toResponse(p);
    }

    // --------- UPDATE ----------
    @Override
    public PositionResponse updatePosition(Long id, PositionRequest request) {
        Position p = positionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Position not found: " + id));

        p.setName(request.getName());
        p.setDescription(request.getDescription());
        p.setStatus(request.getStatus());

        if (request.getDepartmentId() != null) {
            Department d = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new EntityNotFoundException("Department not found: " + request.getDepartmentId()));
            p.setDepartment(d);
        } else {
            // Nếu muốn cho phép bỏ phòng ban:
            // p.setDepartment(null);
        }

        return toResponse(p);
    }

    // --------- DELETE ----------
    @Override
    public void deletePosition(Long id) {
        if (!positionRepository.existsById(id)) {
            throw new EntityNotFoundException("Position not found: " + id);
        }
        positionRepository.deleteById(id);
    }

    // --------- READ ALL (PAGED) ----------
    @Override
    @Transactional(readOnly = true)
    public Page<PositionResponse> getAllPositions(Pageable pageable) {
        return positionRepository.findAll(pageable).map(this::toResponse);
    }

    // --------- BY DEPARTMENT ----------
    @Override
    @Transactional(readOnly = true)
    public List<PositionResponse> getPositionsByDepartment(Long departmentId) {
        return positionRepository.findByDepartmentId(departmentId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ====== MAPPER ======
    private PositionResponse toResponse(Position p) {
        Department d = p.getDepartment();
        return PositionResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .status(p.getStatus())
                .departmentId(d != null ? d.getId() : null)
                .departmentName(d != null ? d.getName() : null)
                .build();
    }

}
