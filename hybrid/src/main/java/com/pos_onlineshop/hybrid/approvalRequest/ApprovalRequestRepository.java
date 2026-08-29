package com.pos_onlineshop.hybrid.approvalRequest;

import com.pos_onlineshop.hybrid.enums.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {
    List<ApprovalRequest> findAllByOrderByIdDesc();

    List<ApprovalRequest> findByStatusOrderByIdDesc(ApprovalStatus status);

    List<ApprovalRequest> findByEntityTypeAndEntityIdOrderByIdDesc(String entityType, Long entityId);
}
