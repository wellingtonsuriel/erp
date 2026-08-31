package com.pos_onlineshop.hybrid.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pos_onlineshop.hybrid.idempotency.IdempotencyConflictException;
import com.pos_onlineshop.hybrid.idempotency.IdempotentRequest;
import com.pos_onlineshop.hybrid.idempotency.IdempotentRequestRepository;
import com.pos_onlineshop.hybrid.idempotency.IdempotentRequestStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Whole-HTTP-request idempotency, keyed by (endpoint, client-supplied Idempotency-Key) - see
 * IdempotentRequest's class comment for why this is a separate guarantee from
 * JournalEntry.idempotencyKey. A controller wraps its whole handler with begin()/complete()/
 * abandon(): begin() either signals "proceed" (nothing recorded yet) or returns the original
 * response to replay; complete() records the real outcome once the operation actually
 * succeeds; abandon() removes the placeholder if the operation failed, so a genuine retry
 * with the same key can try again instead of being permanently stuck as "in progress".
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final IdempotentRequestRepository idempotentRequestRepository;
    private final ObjectMapper objectMapper;

    /**
     * @return empty if the caller should proceed with the operation; present (COMPLETED) if
     *         this exact request was already handled and its stored response should be
     *         replayed verbatim instead of running the operation again.
     * @throws IdempotencyConflictException if the key was reused with a different request body,
     *         or another request with the same key is currently in progress (including the
     *         case where this call itself lost a race to insert the placeholder row).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<IdempotentRequest> begin(String endpoint, String idempotencyKey, Object requestBody) {
        String requestHash = hash(requestBody);

        Optional<IdempotentRequest> existing = idempotentRequestRepository.findByEndpointAndIdempotencyKey(endpoint, idempotencyKey);
        if (existing.isPresent()) {
            IdempotentRequest record = existing.get();
            if (!record.getRequestHash().equals(requestHash)) {
                throw new IdempotencyConflictException(
                        "Idempotency-Key '" + idempotencyKey + "' was already used with a different request");
            }
            if (record.getStatus() == IdempotentRequestStatus.IN_PROGRESS) {
                throw new IdempotencyConflictException(
                        "A request with Idempotency-Key '" + idempotencyKey + "' is already being processed - retry shortly");
            }
            log.info("Idempotency replay for {} key {}", endpoint, idempotencyKey);
            return Optional.of(record);
        }

        IdempotentRequest record = IdempotentRequest.builder()
                .endpoint(endpoint)
                .idempotencyKey(idempotencyKey)
                .requestHash(requestHash)
                .status(IdempotentRequestStatus.IN_PROGRESS)
                .createdAt(LocalDateTime.now())
                .build();
        try {
            idempotentRequestRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException e) {
            // Lost a concurrent race to insert the placeholder row for this exact key.
            throw new IdempotencyConflictException(
                    "A request with Idempotency-Key '" + idempotencyKey + "' is already being processed - retry shortly");
        }
        return Optional.empty();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String endpoint, String idempotencyKey, int responseStatus, Object responseBody) {
        idempotentRequestRepository.findByEndpointAndIdempotencyKey(endpoint, idempotencyKey).ifPresent(record -> {
            record.setStatus(IdempotentRequestStatus.COMPLETED);
            record.setResponseStatus(responseStatus);
            record.setResponseBody(writeJson(responseBody));
            record.setCompletedAt(LocalDateTime.now());
            idempotentRequestRepository.save(record);
        });
    }

    /** Removes the in-progress placeholder after a failed attempt, so a genuine retry with the
     * same key can attempt the operation again instead of being stuck reporting "in progress"
     * forever. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void abandon(String endpoint, String idempotencyKey) {
        idempotentRequestRepository.findByEndpointAndIdempotencyKey(endpoint, idempotencyKey)
                .filter(record -> record.getStatus() == IdempotentRequestStatus.IN_PROGRESS)
                .ifPresent(idempotentRequestRepository::delete);
    }

    private String hash(Object requestBody) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(requestBody);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(json);
            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed available on every JVM - this branch cannot occur in practice.
            throw new IllegalStateException(e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash idempotent request body", e);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize idempotent response body", e);
        }
    }
}
