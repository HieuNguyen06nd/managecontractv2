package com.hieunguyen.ManageContract.mapper;

import com.hieunguyen.ManageContract.dto.category.CategoryCreateRequest;
import com.hieunguyen.ManageContract.dto.category.CategoryResponse;
import com.hieunguyen.ManageContract.dto.category.CategoryUpdateRequest;
import com.hieunguyen.ManageContract.entity.Category;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public CategoryResponse toResponse(Category category) {
        if (category == null) {
            return null;
        }

        return CategoryResponse.builder()
                .id(category.getId())
                .code(category.getCode())
                .name(category.getName())
                .description(category.getDescription())
                .status(category.getStatus())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }

    public Category toEntity(CategoryCreateRequest request) {
        if (request == null) {
            return null;
        }

        return Category.builder()
                .code(request.getCode())
                .name(request.getName())
                .description(request.getDescription())
                .build();
    }

    public void updateEntityFromRequest(Category category, CategoryUpdateRequest request) {
        if (category == null || request == null) {
            return;
        }

        category.setCode(request.getCode());
        category.setName(request.getName());
        category.setDescription(request.getDescription());

        if (request.getStatus() != null) {
            category.setStatus(request.getStatus());
        }
    }
}