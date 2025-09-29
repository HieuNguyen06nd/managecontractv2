package com.hieunguyen.ManageContract.repository;

import com.hieunguyen.ManageContract.entity.Department;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    @Query("SELECT d FROM Department d LEFT JOIN FETCH d.parent LEFT JOIN FETCH d.leader")
    List<Department> findAllWithParentAndLeader();

    @Query("SELECT d FROM Department d LEFT JOIN FETCH d.parent LEFT JOIN FETCH d.leader WHERE d.id = :id")
    Optional<Department> findByIdWithParentAndLeader(Long id);

    @Query("SELECT DISTINCT d FROM Department d " +
            "LEFT JOIN FETCH d.parent " +
            "LEFT JOIN FETCH d.leader " +
            "LEFT JOIN FETCH d.employees")
    Page<Department> findAllWithParentAndLeaderAndEmployees(Pageable pageable);
}
