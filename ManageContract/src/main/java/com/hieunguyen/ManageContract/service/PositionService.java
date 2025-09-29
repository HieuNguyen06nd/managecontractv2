package com.hieunguyen.ManageContract.service;

import com.hieunguyen.ManageContract.dto.position.PositionRequest;
import com.hieunguyen.ManageContract.dto.position.PositionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PositionService {
    List<PositionResponse> getAllPositions();
    PositionResponse getPositionById(Long id);
    PositionResponse createPosition(PositionRequest request);
    PositionResponse updatePosition(Long id, PositionRequest request);
    void deletePosition(Long id);
    Page<PositionResponse> getAllPositions(Pageable pageable);
}