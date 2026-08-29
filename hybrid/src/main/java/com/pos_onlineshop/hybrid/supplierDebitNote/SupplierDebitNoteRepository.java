package com.pos_onlineshop.hybrid.supplierDebitNote;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupplierDebitNoteRepository extends JpaRepository<SupplierDebitNote, Long> {
    boolean existsByDebitNoteNumber(String debitNoteNumber);

    List<SupplierDebitNote> findAllByOrderByIdDesc();
}
