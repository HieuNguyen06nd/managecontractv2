package com.hieunguyen.ManageContract.entity;

import com.hieunguyen.ManageContract.common.constants.NotificationType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Data
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String message;

    @Enumerated(EnumType.STRING)
    private NotificationType type;

    private Boolean isRead = false;
    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToOne @JoinColumn(name = "contract_id")
    private Contract contract;   // để biết notify gắn với hợp đồng nào

    @ManyToOne @JoinColumn(name = "approval_step_id")
    private ApprovalStep step;   // notify phát sinh ở bước nào (nếu liên quan phê duyệt)

    @ManyToOne @JoinColumn(name = "recipient_id")
    private User recipient;
}

