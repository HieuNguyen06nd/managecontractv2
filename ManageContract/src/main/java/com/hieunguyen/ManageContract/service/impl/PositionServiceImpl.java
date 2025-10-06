package com.hieunguyen.ManageContract.service.impl;

import com.hieunguyen.ManageContract.common.exception.ResourceNotFoundException;
import com.hieunguyen.ManageContract.dto.position.PositionRequest;
import com.hieunguyen.ManageContract.dto.position.PositionResponse;
import com.hieunguyen.ManageContract.entity.Position;
import com.hieunguyen.ManageContract.repository.PositionRepository;
import com.hieunguyen.ManageContract.service.PositionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PositionServiceImpl implements PositionService {

    private final PositionRepository positionRepository;

    @Override
    public List<PositionResponse> getAllPositions() {
        return positionRepository.findAll().stream()
                .map(this::mapToPositionResponse)
                .collect(Collectors.toList());
    }

    @Override
    public Page<PositionResponse> getAllPositions(Pageable pageable) {
        return positionRepository.findAll(pageable)
                .map(this::mapToPositionResponse);
    }

    @Override
    public PositionResponse getPositionById(Long id) {
        Position position = positionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Position not found with id: " + id));
        return mapToPositionResponse(position);
    }

    @Override
    public PositionResponse createPosition(PositionRequest request) {
        Position position = new Position();
        position.setName(request.getName());
        position.setDescription(request.getDescription());
        position.setStatus(request.getStatus());
        Position savedPosition = positionRepository.save(position);
        return mapToPositionResponse(savedPosition);
    }

    @Override
    public List<PositionResponse> getPositionsByDepartment(Long departmentId) {
        List<Position> positions = positionRepository.findByDepartmentId(departmentId);
        if (positions.isEmpty()) {
            throw new ResourceNotFoundException("No positions found for department with id: " + departmentId);
        }
        return positions.stream()
                .map(this::mapToPositionResponse)
                .collect(Collectors.toList());
    }


    @Override
    public PositionResponse updatePosition(Long id, PositionRequest request) {
        Position position = positionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Position not found with id: " + id));
        position.setName(request.getName());
        position.setDescription(request.getDescription());
        position.setStatus(request.getStatus());
        Position updatedPosition = positionRepository.save(position);
        return mapToPositionResponse(updatedPosition);
    }

    @Override
    public void deletePosition(Long id) {
        if (!positionRepository.existsById(id)) {
            throw new ResourceNotFoundException("Position not found with id: " + id);
        }
        positionRepository.deleteById(id);
    }

    private PositionResponse mapToPositionResponse(Position position) {
        return PositionResponse.builder()
                .id(position.getId())
                .name(position.getName())
                .description(position.getDescription())
                .status(position.getStatus())
                .build();
    }
}