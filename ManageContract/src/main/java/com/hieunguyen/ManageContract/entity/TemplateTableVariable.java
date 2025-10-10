package com.hieunguyen.ManageContract.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.hieunguyen.ManageContract.common.constants.VariableType;
import jakarta.persistence.*;
import lombok.Data;

import java.util.List;

@Entity
@Table(name = "template_table_variables")
@Data
public class TemplateTableVariable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String tableName; // "products", "invoice_items", etc.
    private String displayName; // "Danh sách sản phẩm", "Chi tiết hóa đơn"

    // Quan hệ với Template
    @ManyToOne
    @JoinColumn(name = "template_id")
    @JsonBackReference
    private ContractTemplate template;

    // Danh sách các cột trong bảng
    @OneToMany(mappedBy = "tableVariable", cascade = CascadeType.ALL)
    @JsonManagedReference
    private List<TableColumn> columns;

    @Column(name = "editable")
    private Boolean editable = true;

    private Integer minRows = 1;
    private Integer maxRows = 100;
    private Integer orderIndex;
}
