package com.hieunguyen.ManageContract.entity;

import com.hieunguyen.ManageContract.common.constants.Status;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "positions")
@Data
public class Position {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String description;
    private Status status;
}