package com.hieunguyen.ManageContract.repository;

import com.hieunguyen.ManageContract.entity.Category;
import com.hieunguyen.ManageContract.common.constants.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    // ===== CÁC METHOD CẦN THIẾT CHO SERVICE =====

    // Tìm category theo code
    Optional<Category> findByCode(String code);

    // Tìm category theo status
    List<Category> findByStatus(Status status);

    // Tìm category active sắp xếp theo name
    List<Category> findByStatusOrderByNameAsc(Status status);

    // Kiểm tra code đã tồn tại (cho create)
    boolean existsByCode(String code);

    // Kiểm tra code đã tồn tại (cho update - trừ id hiện tại)
    @Query("SELECT COUNT(c) > 0 FROM Category c WHERE c.code = :code AND c.id != :id")
    boolean existsByCodeAndIdNot(@Param("code") String code, @Param("id") Long id);

    // Tìm kiếm categories theo keyword (active only)
    @Query("SELECT c FROM Category c WHERE c.status = :status AND " +
            "(LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(c.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "ORDER BY c.name ASC")
    List<Category> searchActiveCategories(@Param("keyword") String keyword, @Param("status") Status status);
}