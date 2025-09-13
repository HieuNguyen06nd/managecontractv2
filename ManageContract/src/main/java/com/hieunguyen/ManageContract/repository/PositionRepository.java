package com.hieunguyen.ManageContract.repository;

import com.hieunguyen.ManageContract.entity.Position;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PositionRepository extends JpaRepository<Position, Long> {
}
