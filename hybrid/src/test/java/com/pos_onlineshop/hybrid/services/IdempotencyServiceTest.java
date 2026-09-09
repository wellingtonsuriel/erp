package com.pos_onlineshop.hybrid.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pos_onlineshop.hybrid.idempotency.IdempotencyConflictException;
import com.pos_onlineshop.hybrid.idempotency.IdempotentRequest;
import com.pos_onlineshop.hybrid.idempotency.IdempotentRequestRepository;
import com.pos_onlineshop.hybrid.idempotency.IdempotentRequestStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Whole-HTTP-request idempotency (see IdempotentRequest's class comment for why this is a
 * separate guarantee from JournalEntry.idempotencyKey). These tests prove a retried request
 * either proceeds exactly once or is told to retry/reject - never silently repeats the
 * underlying operation.
 */
@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock private IdempotentRequestRepository repository;

    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new IdempotencyService(repository, new ObjectMapper());
    }

    @Test
    void firstRequestWithANewKeyProceeds() {
        when(repository.findByEndpointAndIdempotencyKey("POST /api/orders", "key-1")).thenReturn(Optional.empty());

        Optional<IdempotentRequest> outcome = service.begin("POST /api/orders", "key-1", Map.of("a", 1));

        assertTrue(outcome.isEmpty());
        ArgumentCaptor<IdempotentRequest> captor = ArgumentCaptor.forClass(IdempotentRequest.class);
        verify(repository).saveAndFlush(captor.capture());
        assertEquals(IdempotentRequestStatus.IN_PROGRESS, captor.getValue().getStatus());
    }

    @Test
    void aRetryWithTheIdenticalKeyAndBodyReplaysTheStoredCompletedResponse() {
        IdempotentRequest completed = IdempotentRequest.builder()
                .endpoint("POST /api/orders").idempotencyKey("key-1")
                .requestHash(hashOf(Map.of("a", 1)))
                .status(IdempotentRequestStatus.COMPLETED)
                .responseStatus(201).responseBody("{\"id\":42}")
                .build();
        when(repository.findByEndpointAndIdempotencyKey("POST /api/orders", "key-1")).thenReturn(Optional.of(completed));

        Optional<IdempotentRequest> outcome = service.begin("POST /api/orders", "key-1", Map.of("a", 1));

        assertTrue(outcome.isPresent());
        assertEquals(201, outcome.get().getResponseStatus());
        assertEquals("{\"id\":42}", outcome.get().getResponseBody());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void reusingTheSameKeyWithADifferentBodyIsRejected() {
        IdempotentRequest completed = IdempotentRequest.builder()
                .endpoint("POST /api/orders").idempotencyKey("key-1")
                .requestHash(hashOf(Map.of("a", 1)))
                .status(IdempotentRequestStatus.COMPLETED)
                .responseStatus(201).responseBody("{\"id\":42}")
                .build();
        when(repository.findByEndpointAndIdempotencyKey("POST /api/orders", "key-1")).thenReturn(Optional.of(completed));

        assertThrows(IdempotencyConflictException.class,
                () -> service.begin("POST /api/orders", "key-1", Map.of("a", 2)));
    }

    @Test
    void aSecondRequestWhileTheFirstIsStillInProgressIsRejectedRatherThanProceeding() {
        IdempotentRequest inProgress = IdempotentRequest.builder()
                .endpoint("POST /api/orders").idempotencyKey("key-1")
                .requestHash(hashOf(Map.of("a", 1)))
                .status(IdempotentRequestStatus.IN_PROGRESS)
                .build();
        when(repository.findByEndpointAndIdempotencyKey("POST /api/orders", "key-1")).thenReturn(Optional.of(inProgress));

        assertThrows(IdempotencyConflictException.class,
                () -> service.begin("POST /api/orders", "key-1", Map.of("a", 1)));
    }

    @Test
    void losingTheInsertRaceToAConcurrentRequestIsTreatedAsInProgressNotAServerError() {
        when(repository.findByEndpointAndIdempotencyKey("POST /api/orders", "key-1")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThrows(IdempotencyConflictException.class,
                () -> service.begin("POST /api/orders", "key-1", Map.of("a", 1)));
    }

    @Test
    void completeStoresTheRealResponseSoALaterReplayReturnsIt() {
        IdempotentRequest record = IdempotentRequest.builder()
                .endpoint("POST /api/orders").idempotencyKey("key-1")
                .requestHash(hashOf(Map.of("a", 1)))
                .status(IdempotentRequestStatus.IN_PROGRESS)
                .build();
        when(repository.findByEndpointAndIdempotencyKey("POST /api/orders", "key-1")).thenReturn(Optional.of(record));

        service.complete("POST /api/orders", "key-1", 201, Map.of("id", 42));

        ArgumentCaptor<IdempotentRequest> captor = ArgumentCaptor.forClass(IdempotentRequest.class);
        verify(repository).save(captor.capture());
        assertEquals(IdempotentRequestStatus.COMPLETED, captor.getValue().getStatus());
        assertEquals(201, captor.getValue().getResponseStatus());
        assertEquals("{\"id\":42}", captor.getValue().getResponseBody());
    }

    @Test
    void abandonRemovesOnlyAnInProgressPlaceholderSoARetryCanTryAgain() {
        IdempotentRequest inProgress = IdempotentRequest.builder()
                .id(7L).endpoint("POST /api/orders").idempotencyKey("key-1")
                .status(IdempotentRequestStatus.IN_PROGRESS).build();
        when(repository.findByEndpointAndIdempotencyKey("POST /api/orders", "key-1")).thenReturn(Optional.of(inProgress));

        service.abandon("POST /api/orders", "key-1");

        verify(repository).delete(inProgress);
    }

    @Test
    void abandonNeverDeletesAnAlreadyCompletedRecord() {
        IdempotentRequest completed = IdempotentRequest.builder()
                .id(7L).endpoint("POST /api/orders").idempotencyKey("key-1")
                .status(IdempotentRequestStatus.COMPLETED).build();
        when(repository.findByEndpointAndIdempotencyKey("POST /api/orders", "key-1")).thenReturn(Optional.of(completed));

        service.abandon("POST /api/orders", "key-1");

        verify(repository, never()).delete(any());
    }

    /** Mirrors IdempotencyService's private hash() via reflection, so these tests can set up
     * a stored requestHash that will actually match/mismatch a given body the same way the
     * real begin() call computes it - without duplicating (and risking drifting from) the
     * hashing logic itself. */
    private String hashOf(Object body) {
        try {
            var method = IdempotencyService.class.getDeclaredMethod("hash", Object.class);
            method.setAccessible(true);
            return (String) method.invoke(service, body);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
