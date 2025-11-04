// ContractApprovalMailListener.java
package com.hieunguyen.ManageContract.listener;

import com.hieunguyen.ManageContract.common.constants.ApprovalStatus;
import com.hieunguyen.ManageContract.entity.ContractApproval;
import com.hieunguyen.ManageContract.entity.ContractSignature;
import com.hieunguyen.ManageContract.event.ContractApprovalEvent;
import com.hieunguyen.ManageContract.repository.ContractApprovalRepository;
import com.hieunguyen.ManageContract.repository.ContractSignatureRepository;
import com.hieunguyen.ManageContract.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContractApprovalMailListener {

    private final ContractApprovalRepository approvalRepo;
    private final ContractSignatureRepository signatureRepo;   // ✅ inject thêm
    private final EmailService emailService;

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApprovalChanged(ContractApprovalEvent event) {
        ContractApproval a = approvalRepo.findById(event.approvalId()).orElse(null);
        if (a == null) {
            log.warn("[MAIL] Không tìm thấy ContractApproval id={}", event.approvalId());
            return;
        }

        // Build contract label (không cần getCode)
        String contractLabel = buildContractLabel(a);
        String approverName = safe(() -> a.getApprover().getFullName(),
                safe(() -> a.getApprover().getAccount().getEmail(), "Người phê duyệt"));
        String currentApproverEmail = safe(() -> a.getApprover().getAccount().getEmail(), null);
        LocalDateTime decidedAt = a.getApprovedAt();

        // 🎯 Tập người nhận: người tạo + người ký ngay trước đó (nếu có), loại trùng
        Set<String> recipients = new LinkedHashSet<>();
        addIfPresent(recipients, getCreatorEmail(a));

        String prevSigner = getPreviousSignerEmail(a, decidedAt, currentApproverEmail);
        addIfPresent(recipients, prevSigner);

        if (recipients.isEmpty()) {
            log.warn("[MAIL] Không có người nhận | contract={} | approvalId={}", contractLabel, a.getId());
            return;
        }

        // Gửi cho từng người
        for (String to : recipients) {
            try {
                if (event.status() == ApprovalStatus.APPROVED) {
                    emailService.sendContractApproved(to, contractLabel, approverName, decidedAt);
                    log.info("[MAIL] APPROVED → {} | contract={} | at={}",
                            to, contractLabel, decidedAt != null ? decidedAt.format(TS) : "n/a");
                } else if (event.status() == ApprovalStatus.REJECTED) {
                    String reason = a.getComment();
                    emailService.sendContractRejected(to, contractLabel, approverName, reason, decidedAt);
                    log.info("[MAIL] REJECTED → {} | contract={} | at={} | reason={}",
                            to, contractLabel, decidedAt != null ? decidedAt.format(TS) : "n/a", orEmpty(reason));
                } else {
                    log.debug("[MAIL] Bỏ qua trạng thái: {}", event.status());
                }
            } catch (Exception ex) {
                log.error("[MAIL] Lỗi khi gửi mail → {} | contract={} | status={} | err={}",
                        to, contractLabel, event.status(), ex.getMessage(), ex);
            }
        }
    }

    // ---------- Helpers ----------

    private String getCreatorEmail(ContractApproval a) {
        try {
            var createdBy = a.getContract().getCreatedBy();
            if (createdBy != null && createdBy.getAccount() != null) {
                String email = createdBy.getAccount().getEmail();
                if (email != null && !email.isBlank()) return email;
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Lấy email người ký NGAY TRƯỚC thời điểm hiện tại.
     * - Ưu tiên chữ ký có signedAt < decidedAt (của approval hiện tại).
     * - Nếu decidedAt null (edge case), fallback chữ ký mới nhất.
     * - Bỏ qua nếu trùng người hiện tại.
     */
    private String getPreviousSignerEmail(ContractApproval a, LocalDateTime decidedAt, String currentApproverEmail) {
        Long contractId = safe(() -> a.getContract().getId(), null);
        if (contractId == null) return null;

        Optional<ContractSignature> optPrev =
                (decidedAt != null)
                        ? signatureRepo.findTopByContract_IdAndSignedAtBeforeOrderBySignedAtDesc(contractId, decidedAt)
                        : signatureRepo.findTopByContract_IdOrderBySignedAtDesc(contractId);

        if (optPrev.isEmpty()) return null;

        var prev = optPrev.get();
        String email = safe(() -> prev.getSigner().getAccount().getEmail(), null);
        if (email == null || email.isBlank()) return null;

        // loại trùng với người vừa duyệt/ký
        if (currentApproverEmail != null && currentApproverEmail.equalsIgnoreCase(email)) return null;

        return email;
    }

    private String buildContractLabel(ContractApproval a) {
        try {
            Long id = a.getContract().getId();
            return "HĐ#" + (id != null ? id : -1L);
        } catch (Exception e) {
            return "HĐ";
        }
    }

    private static void addIfPresent(Set<String> set, String email) {
        if (email != null && !email.isBlank()) set.add(email);
    }

    private static String orEmpty(String s) { return s == null ? "" : s; }

    private static <T> T safe(SupplierEx<T> s, T fb) {
        try { return s.get(); } catch (Exception e) { return fb; }
    }
    @FunctionalInterface interface SupplierEx<T> { T get() throws Exception; }
}
