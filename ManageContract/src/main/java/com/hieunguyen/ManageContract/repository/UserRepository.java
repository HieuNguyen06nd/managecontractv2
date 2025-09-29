package com.hieunguyen.ManageContract.repository;

import com.hieunguyen.ManageContract.entity.AuthAccount;
import com.hieunguyen.ManageContract.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<Employee, Long> {
    boolean existsByAccount(AuthAccount account);
    Optional<Employee> findByAccount_Email(String email);
    Optional<Employee> findByAccount_Id(Long accountId);

}
