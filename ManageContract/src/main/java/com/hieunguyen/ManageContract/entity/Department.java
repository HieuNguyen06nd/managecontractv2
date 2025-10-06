package com.hieunguyen.ManageContract.entity;

import com.hieunguyen.ManageContract.common.constants.Status;
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
    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    private Set<Department> children = new HashSet<>();

    // Leader của phòng ban (một user cụ thể)
    @ManyToOne
    @JoinColumn(name = "leader_id")
    private Employee leader;

    // Danh sách nhân viên trong phòng ban
    @OneToMany(mappedBy = "department", fetch = FetchType.LAZY)
    private Set<Employee> employees = new HashSet<>();

    private Status status;

    @OneToMany(mappedBy = "department")
    private Set<Position> positions = new HashSet<>();
}


