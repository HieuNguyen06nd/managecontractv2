package com.hieunguyen.ManageContract.entity;

import com.nimbusds.openid.connect.sdk.claims.Gender;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "employees")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Employee {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "account_id")
    private AuthAccount account;

    private String fullName;
    private String phone;
    private String signatureImage;

    @Column
    private LocalDateTime signatureUpdatedAt;

    private Gender gender;

    @ManyToOne @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne @JoinColumn(name = "position_id")
    private Position position;
}

