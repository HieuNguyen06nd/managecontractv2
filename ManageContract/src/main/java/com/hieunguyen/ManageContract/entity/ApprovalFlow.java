package com.hieunguyen.ManageContract.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "approval_flows")
@Getter
@Setter
@NoArgsConstructor
public class ApprovalFlow {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String description;

    private Boolean allowCustomFlow = true; // mặc định cho sửa

    @ManyToOne @JoinColumn(name = "template_id")
    private ContractTemplate template;

    @OneToMany(mappedBy = "flow", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private Set<ApprovalStep> steps = new HashSet<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ApprovalFlow flow)) return false;
        return id != null && id.equals(flow.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}

