package com.hieunguyen.ManageContract.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.hieunguyen.ManageContract.common.constants.VariableType;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "table_columns")
@Data
public class TableColumn {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String columnName; // "product_name", "quantity", "price"
    private String displayName; // "Tên sản phẩm", "Số lượng", "Đơn giá"

    @Enumerated(EnumType.STRING)
    private VariableType columnType; // STRING, NUMBER, DATE, etc.

    private Boolean required;
    private Integer columnOrder;

    // Quan hệ với bảng
    @ManyToOne
    @JoinColumn(name = "table_variable_id")
    @JsonBackReference
    private TemplateTableVariable tableVariable;
}
