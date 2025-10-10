package com.hieunguyen.ManageContract.repository;

import com.hieunguyen.ManageContract.entity.TableColumn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TableColumnRepository extends JpaRepository<TableColumn, Long> {
    List<TableColumn> findByTableVariableId(Long tableVariableId);
}