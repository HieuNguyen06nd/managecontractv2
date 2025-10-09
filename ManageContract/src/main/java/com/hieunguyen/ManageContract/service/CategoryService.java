package com.hieunguyen.ManageContract.service;

import com.hieunguyen.ManageContract.dto.category.CategoryCreateRequest;
import com.hieunguyen.ManageContract.dto.category.CategoryResponse;
import com.hieunguyen.ManageContract.dto.category.CategoryUpdateRequest;

import java.util.List;

public interface CategoryService {

    // Lấy tất cả categories (active + inactive)
    List<CategoryResponse> getAllCategories();

    // Lấy danh sách categories active
    List<CategoryResponse> getActiveCategories();

    // Tìm kiếm categories theo keyword
    List<CategoryResponse> searchCategories(String keyword);

    // Lấy category theo ID
    CategoryResponse getCategoryById(Long id);

    // Lấy category theo code
    CategoryResponse getCategoryByCode(String code);

    // Tạo category mới
    CategoryResponse createCategory(CategoryCreateRequest request);

    // Cập nhật category
    CategoryResponse updateCategory(Long id, CategoryUpdateRequest request);

    // Xóa category (soft delete - set status = INACTIVE)
    void deleteCategory(Long id);

    // Kích hoạt category
    CategoryResponse activateCategory(Long id);

    // Vô hiệu hóa category
    CategoryResponse deactivateCategory(Long id);
}
