package com.pos_onlineshop.hybrid.customers;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CustomersRepository extends JpaRepository<Customers, Long> {

    Optional<Customers> findByCode(String code);

    Optional<Customers> findByTaxId(String taxId);

    Optional<Customers> findByEmail(String email);

    List<Customers> findByActiveTrue();

    List<Customers> findByVerifiedTrue();

    List<Customers> findByActiveTrueAndVerifiedTrue();

    boolean existsByCode(String code);

    boolean existsByTaxId(String taxId);

    boolean existsByEmail(String email);

    @Query("SELECT c FROM Customers c WHERE c.active = true AND c.verified = true ORDER BY c.name")
    List<Customers> findAllActiveAndVerifiedOrdered();

    @Query("SELECT c FROM Customers c WHERE c.active = true ORDER BY c.name")
    List<Customers> findAllActiveOrdered();

    @Query("SELECT c FROM Customers c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR LOWER(c.code) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR LOWER(c.email) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<Customers> searchByNameOrCodeOrEmail(String searchTerm);

    List<Customers> findByCountry(String country);

    List<Customers> findByCity(String city);

    @Query("SELECT c FROM Customers c WHERE c.loyaltyPoints >= :minPoints AND c.active = true ORDER BY c.loyaltyPoints DESC")
    List<Customers> findByMinLoyaltyPoints(Integer minPoints);
}
