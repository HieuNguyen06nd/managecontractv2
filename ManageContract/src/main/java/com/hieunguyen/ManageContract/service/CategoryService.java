package com.hieunguyen.ManageContract.service;

import com.hieunguyen.ManageContract.dto.category.CategoryRequest;
import com.hieunguyen.ManageContract.dto.category.CategoryResponse;

import java.util.List;

public interface CategoryService {
    CategoryResponse create(CategoryRequest req);
    CategoryResponse update(Long id, CategoryRequest req);
    void delete(Long id);
    List<CategoryResponse> findAll();
    CategoryResponse findById(Long id);
}
