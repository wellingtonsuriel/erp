package com.pos_onlineshop.hybrid.idempotency;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Durable, whole-HTTP-request idempotency record - distinct from JournalEntry.idempotencyKey,
 * which only protects the accounting posting itself. A client-supplied Idempotency-Key header
 * on an operation like order creation must guarantee the *entire* operation (inventory
 * reservation, order row, GL posting) runs exactly once even if the HTTP request is retried
 * after a timeout, dropped connection, or client-side retry loop - GL-level idempotency alone
 * would still let a retry create a second Order row reserving stock a second time. The unique
 * constraint on (endpoint, idempotency_key) is the actual concurrency guard: two requests
 * racing on the same key will have one insert succeed and one fail with a constraint
 * violation, which IdempotencyService turns into "still in progress, retry shortly" rather
 * than letting both proceed.
 */
@Entity
@Table(name = "idempotent_requests", uniqueConstraints = @UniqueConstraint(columnNames = {"endpoint", "idempotency_key"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotentRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String endpoint;

    @Column(name = "idempotency_key", nullable = false, length = 200)
    private String idempotencyKey;

    /** SHA-256 hex of the request body - detects a client reusing the same key for a
     * genuinely different request, which must be rejected rather than silently replayed. */
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private IdempotentRequestStatus status = IdempotentRequestStatus.IN_PROGRESS;

    @Column(name = "response_status")
    private Integer responseStatus;

    // Explicit LONGTEXT rather than relying on @Lob's default JDBC-type inference, which
    // mapped this to MySQL TINYTEXT (255 bytes) - nowhere near enough for a serialized
    // response body of any entity with a handful of nested fields (e.g. a full Order).
    @Column(name = "response_body", columnDefinition = "LONGTEXT")
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
