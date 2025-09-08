package com.hieunguyen.ManageContract.repository;

import com.hieunguyen.ManageContract.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
}
