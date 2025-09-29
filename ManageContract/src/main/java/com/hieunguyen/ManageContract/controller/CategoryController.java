package com.hieunguyen.ManageContract.controller;

import com.hieunguyen.ManageContract.dto.ResponseData;
import com.hieunguyen.ManageContract.dto.category.CategoryRequest;
import com.hieunguyen.ManageContract.dto.category.CategoryResponse;
import com.hieunguyen.ManageContract.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService service;

    /**
     * Tạo mới một danh mục (Category)
     */
    @PostMapping
    public ResponseEntity<ResponseData<CategoryResponse>> create(@Valid @RequestBody CategoryRequest req) {
        CategoryResponse response = service.create(req);
        // Trả về mã HTTP 201 CREATED cho thao tác tạo mới thành công
        return new ResponseEntity<>(
                new ResponseData<>(201, "Tạo danh mục thành công", response),
                HttpStatus.CREATED
        );
    }

    /**
     * Cập nhật thông tin danh mục theo ID
     */
    @PutMapping("/{id}")
    public ResponseData<CategoryResponse> update(@PathVariable Long id,
                                                 @Valid @RequestBody CategoryRequest req) {
        CategoryResponse response = service.update(id, req);
        // Mã 200 OK cho cập nhật thành công
        return new ResponseData<>(200, "Cập nhật danh mục thành công", response);
    }

    /**
     * Xóa danh mục theo ID
     */
    @DeleteMapping("/{id}")
    public ResponseData<Void> delete(@PathVariable Long id) {
        service.delete(id);
        // Mã 200 OK (hoặc 204 No Content nếu không muốn trả về body)
        return new ResponseData<>(200, "Xóa danh mục thành công", null);
    }

    /**
     * Lấy danh sách tất cả danh mục
     */
    @GetMapping
    public ResponseData<List<CategoryResponse>> findAll() {
        List<CategoryResponse> categories = service.findAll();
        return new ResponseData<>(200, "Lấy danh sách danh mục thành công", categories);
    }

    /**
     * Lấy thông tin chi tiết danh mục theo ID
     */
    @GetMapping("/{id}")
    public ResponseData<CategoryResponse> findById(@PathVariable Long id) {
        CategoryResponse response = service.findById(id);
        return new ResponseData<>(200, "Lấy thông tin danh mục thành công", response);
    }
}