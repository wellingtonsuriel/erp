package com.pos_onlineshop.hybrid.customerCreditNote;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerCreditNoteRepository extends JpaRepository<CustomerCreditNote, Long> {
    boolean existsByCreditNoteNumber(String creditNoteNumber);

    List<CustomerCreditNote> findAllByOrderByIdDesc();
}
