package com.hieunguyen.ManageContract.controller;

import com.hieunguyen.ManageContract.dto.category.CategoryCreateRequest;
import com.hieunguyen.ManageContract.dto.category.CategoryResponse;
import com.hieunguyen.ManageContract.dto.category.CategoryUpdateRequest;
import com.hieunguyen.ManageContract.dto.ResponseData;
import com.hieunguyen.ManageContract.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "Category", description = "Quản lý danh mục hợp đồng")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "Lấy tất cả danh mục")
    public ResponseData<List<CategoryResponse>> getAllCategories() {
        List<CategoryResponse> categories = categoryService.getAllCategories();
        return new ResponseData<>(200, "Lấy danh sách categories thành công", categories);
    }

    @GetMapping("/active")
    @Operation(summary = "Lấy danh sách danh mục active")
    public ResponseData<List<CategoryResponse>> getActiveCategories() {
        List<CategoryResponse> categories = categoryService.getActiveCategories();
        return new ResponseData<>(200, "Lấy danh sách categories active thành công", categories);
    }

    @GetMapping("/search")
    @Operation(summary = "Tìm kiếm danh mục")
    public ResponseData<List<CategoryResponse>> searchCategories(
            @RequestParam(required = false) String keyword) {
        List<CategoryResponse> categories = categoryService.searchCategories(keyword);
        return new ResponseData<>(200, "Tìm kiếm categories thành công", categories);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy danh mục theo ID")
    public ResponseData<CategoryResponse> getCategoryById(@PathVariable Long id) {
        CategoryResponse category = categoryService.getCategoryById(id);
        return new ResponseData<>(200, "Lấy category thành công", category);
    }

    @GetMapping("/code/{code}")
    @Operation(summary = "Lấy danh mục theo code")
    public ResponseData<CategoryResponse> getCategoryByCode(@PathVariable String code) {
        CategoryResponse category = categoryService.getCategoryByCode(code);
        return new ResponseData<>(200, "Lấy category thành công", category);
    }

    @PostMapping
    @Operation(summary = "Tạo danh mục mới")
    public ResponseData<CategoryResponse> createCategory(@Valid @RequestBody CategoryCreateRequest request) {
        CategoryResponse category = categoryService.createCategory(request);
        return new ResponseData<>(200, "Tạo category thành công", category);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật danh mục")
    public ResponseData<CategoryResponse> updateCategory(@PathVariable Long id,
                                                         @Valid @RequestBody CategoryUpdateRequest request) {
        CategoryResponse category = categoryService.updateCategory(id, request);
        return new ResponseData<>(200, "Cập nhật category thành công", category);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa danh mục (soft delete)")
    public ResponseData<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return new ResponseData<>(200, "Xóa category thành công", null);
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Kích hoạt danh mục")
    public ResponseData<CategoryResponse> activateCategory(@PathVariable Long id) {
        CategoryResponse category = categoryService.activateCategory(id);
        return new ResponseData<>(200, "Kích hoạt category thành công", category);
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Vô hiệu hóa danh mục")
    public ResponseData<CategoryResponse> deactivateCategory(@PathVariable Long id) {
        CategoryResponse category = categoryService.deactivateCategory(id);
        return new ResponseData<>(200, "Vô hiệu hóa category thành công", category);
    }
}