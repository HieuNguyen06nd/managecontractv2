package com.hieunguyen.ManageContract.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "departments")
@Data
public class Department {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name; // Tên phòng ban (Phòng IT, Phòng Nhân sự...)

    // Cấp phòng ban (ví dụ: 1 = cấp cao nhất, 2 = cấp con...)
    private Integer level;

    // Liên kết đến phòng ban cha (nếu có)
    @ManyToOne
    @JoinColumn(name = "parent_id")
    private Department parent;

    // Danh sách phòng ban con
    @OneToMany(mappedBy = "parent")
    private Set<Department> children = new HashSet<>();

    // Leader của phòng ban (một user cụ thể)
    @ManyToOne
    @JoinColumn(name = "leader_id")
    private User leader;

    // Danh sách nhân viên trong phòng ban
    @OneToMany(mappedBy = "department")
    private Set<User> users = new HashSet<>();
}


