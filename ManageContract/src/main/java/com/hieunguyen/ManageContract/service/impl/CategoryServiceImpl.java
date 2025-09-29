package com.hieunguyen.ManageContract.service.impl;

import com.hieunguyen.ManageContract.common.constants.Status;
import com.hieunguyen.ManageContract.dto.category.CategoryRequest;
import com.hieunguyen.ManageContract.dto.category.CategoryResponse;
import com.hieunguyen.ManageContract.entity.Category;
import com.hieunguyen.ManageContract.repository.CategoryRepository;
import com.hieunguyen.ManageContract.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository repository;

    private CategoryResponse toDto(Category c) {
        return CategoryResponse.builder()
                .id(c.getId())
                .code(c.getCode())
                .name(c.getName())
                .description(c.getDescription())
                .status(c.getStatus())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    @Transactional
    @Override
    public CategoryResponse create(CategoryRequest req) {
        if (repository.existsByCodeIgnoreCase(req.getCode())) {
            throw new IllegalArgumentException("Category code đã tồn tại");
        }
        Category cat = Category.builder()
                .code(req.getCode().trim())
                .name(req.getName().trim())
                .description(req.getDescription())
                .status(req.getStatus() != null ? req.getStatus() : Status.ACTIVE)
                .build();
        return toDto(repository.save(cat));
    }

    @Transactional
    @Override
    public CategoryResponse update(Long id, CategoryRequest req) {
        Category cat = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category không tồn tại"));

        // Nếu đổi code, kiểm tra trùng
        if (!cat.getCode().equalsIgnoreCase(req.getCode())
                && repository.existsByCodeIgnoreCase(req.getCode())) {
            throw new IllegalArgumentException("Category code đã tồn tại");
        }

        cat.setCode(req.getCode().trim());
        cat.setName(req.getName().trim());
        cat.setDescription(req.getDescription());
        if (req.getStatus() != null) cat.setStatus(req.getStatus());

        return toDto(repository.save(cat));
    }

    @Transactional
    @Override
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Category không tồn tại");
        }
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    @Override
    public List<CategoryResponse> findAll() {
        return repository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    @Override
    public CategoryResponse findById(Long id) {
        return repository.findById(id).map(this::toDto)
                .orElseThrow(() -> new IllegalArgumentException("Category không tồn tại"));
    }
}
