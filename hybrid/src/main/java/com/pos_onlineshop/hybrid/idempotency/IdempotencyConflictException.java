package com.pos_onlineshop.hybrid.idempotency;

/** Thrown when an Idempotency-Key cannot be honored as a safe replay: either the same key was
 * reused with a genuinely different request body, or another request with the same key is
 * still in flight. Both are a 409 Conflict - the caller must either resubmit the identical
 * request (if it was really a retry) or wait and retry (if it lost a race). */
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
