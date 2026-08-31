package com.pos_onlineshop.hybrid.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotentRequestRepository extends JpaRepository<IdempotentRequest, Long> {

    Optional<IdempotentRequest> findByEndpointAndIdempotencyKey(String endpoint, String idempotencyKey);
}
