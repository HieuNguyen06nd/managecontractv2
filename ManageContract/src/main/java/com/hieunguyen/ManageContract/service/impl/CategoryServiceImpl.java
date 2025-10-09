package com.hieunguyen.ManageContract.service.impl;

import com.hieunguyen.ManageContract.common.constants.Status;
import com.hieunguyen.ManageContract.common.exception.ResourceNotFoundException;
import com.hieunguyen.ManageContract.dto.category.CategoryCreateRequest;
import com.hieunguyen.ManageContract.dto.category.CategoryResponse;
import com.hieunguyen.ManageContract.dto.category.CategoryUpdateRequest;
import com.hieunguyen.ManageContract.entity.Category;
import com.hieunguyen.ManageContract.mapper.CategoryMapper;
import com.hieunguyen.ManageContract.repository.CategoryRepository;
import com.hieunguyen.ManageContract.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    @Override
    public List<CategoryResponse> getAllCategories() {
        List<Category> categories = categoryRepository.findAll();
        return categories.stream()
                .map(categoryMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<CategoryResponse> getActiveCategories() {
        List<Category> categories = categoryRepository.findByStatusOrderByNameAsc(Status.ACTIVE);
        return categories.stream()
                .map(categoryMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<CategoryResponse> searchCategories(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getActiveCategories();
        }

        List<Category> categories = categoryRepository.searchActiveCategories(
                keyword.trim(), Status.ACTIVE);
        return categories.stream()
                .map(categoryMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public CategoryResponse getCategoryById(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category không tồn tại với ID: " + id));
        return categoryMapper.toResponse(category);
    }

    @Override
    public CategoryResponse getCategoryByCode(String code) {
        Category category = categoryRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Category không tồn tại với code: " + code));
        return categoryMapper.toResponse(category);
    }

    @Override
    @Transactional
    public CategoryResponse createCategory(CategoryCreateRequest request) {
        // Kiểm tra code đã tồn tại chưa
        if (categoryRepository.existsByCode(request.getCode())) {
            throw new IllegalArgumentException("Code đã tồn tại: " + request.getCode());
        }

        Category category = categoryMapper.toEntity(request);
        Category savedCategory = categoryRepository.save(category);

        return categoryMapper.toResponse(savedCategory);
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryUpdateRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category không tồn tại với ID: " + id));

        // Kiểm tra code đã tồn tại (trừ category hiện tại)
        if (categoryRepository.existsByCodeAndIdNot(request.getCode(), id)) {
            throw new IllegalArgumentException("Code đã tồn tại: " + request.getCode());
        }

        categoryMapper.updateEntityFromRequest(category, request);
        Category updatedCategory = categoryRepository.save(category);

        return categoryMapper.toResponse(updatedCategory);
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category không tồn tại với ID: " + id));

        category.setStatus(Status.INACTIVE);
        categoryRepository.save(category);
    }

    @Override
    @Transactional
    public CategoryResponse activateCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category không tồn tại với ID: " + id));

        category.setStatus(Status.ACTIVE);
        Category activatedCategory = categoryRepository.save(category);

        return categoryMapper.toResponse(activatedCategory);
    }

    @Override
    @Transactional
    public CategoryResponse deactivateCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category không tồn tại với ID: " + id));

        category.setStatus(Status.INACTIVE);
        Category deactivatedCategory = categoryRepository.save(category);

        return categoryMapper.toResponse(deactivatedCategory);
    }
}