package com.pos_onlineshop.hybrid.glNumbering;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JournalNumberCounterRepository extends JpaRepository<JournalNumberCounter, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM JournalNumberCounter c WHERE c.id = :id")
    Optional<JournalNumberCounter> findByIdForUpdate(Long id);
}
