package com.hieunguyen.ManageContract.repository;

import com.hieunguyen.ManageContract.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    boolean existsByCodeIgnoreCase(String code);
    Optional<Category> findByCodeIgnoreCase(String code);
}
