package com.hieunguyen.ManageContract.controller;

import com.hieunguyen.ManageContract.dto.ResponseData;
import com.hieunguyen.ManageContract.dto.position.PositionRequest;
import com.hieunguyen.ManageContract.dto.position.PositionResponse;
import com.hieunguyen.ManageContract.service.PositionService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/positions")
@RequiredArgsConstructor
public class PositionController {

    private final PositionService positionService;

    @GetMapping
    @Operation(summary = "Lấy danh sách tất cả chức vụ")
    public ResponseData<List<PositionResponse>> getAllPositions() {
        List<PositionResponse> positions = positionService.getAllPositions();
        return new ResponseData<>(200, "Lấy danh sách chức vụ thành công", positions);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy thông tin chức vụ theo ID")
    public ResponseData<PositionResponse> getPositionById(@PathVariable Long id) {
        PositionResponse position = positionService.getPositionById(id);
        return new ResponseData<>(200, "Lấy thông tin chức vụ thành công", position);
    }

    @PostMapping
    @Operation(summary = "Tạo chức vụ mới")
    public ResponseData<PositionResponse> createPosition(@RequestBody PositionRequest request) {
        PositionResponse newPosition = positionService.createPosition(request);
        return new ResponseData<>(201, "Tạo chức vụ thành công", newPosition);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật chức vụ theo ID")
    public ResponseData<PositionResponse> updatePosition(@PathVariable Long id, @RequestBody PositionRequest request) {
        PositionResponse updatedPosition = positionService.updatePosition(id, request);
        return new ResponseData<>(200, "Cập nhật chức vụ thành công", updatedPosition);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa chức vụ theo ID")
    public ResponseData<Void> deletePosition(@PathVariable Long id) {
        positionService.deletePosition(id);
        return new ResponseData<>(200, "Xóa chức vụ thành công", null);
    }
    @GetMapping("/positions")
    public ResponseData<Page<PositionResponse>> getAllPositions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return new ResponseData<>(200, "Danh sách position", positionService.getAllPositions(pageable));
    }

}