package com.pos_onlineshop.hybrid.customerCreditNote;

import com.pos_onlineshop.hybrid.customers.Customers;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerCreditNoteRepository extends JpaRepository<CustomerCreditNote, Long> {
    boolean existsByCreditNoteNumber(String creditNoteNumber);

    List<CustomerCreditNote> findAllByOrderByIdDesc();

    List<CustomerCreditNote> findByCustomer(Customers customer);
}
