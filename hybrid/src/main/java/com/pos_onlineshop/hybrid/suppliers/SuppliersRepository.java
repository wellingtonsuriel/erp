package com.pos_onlineshop.hybrid.suppliers;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SuppliersRepository extends JpaRepository<Suppliers, Long> {

    Optional<Suppliers> findByCode(String code);

    Optional<Suppliers> findByTaxId(String taxId);

    List<Suppliers> findByActiveTrue();

    List<Suppliers> findByVerifiedTrue();

    List<Suppliers> findByActiveTrueAndVerifiedTrue();

    boolean existsByCode(String code);

    boolean existsByTaxId(String taxId);

    @Query("SELECT s FROM Suppliers s WHERE s.active = true AND s.verified = true ORDER BY s.name")
    List<Suppliers> findAllActiveAndVerifiedOrdered();

    @Query("SELECT s FROM Suppliers s WHERE s.active = true ORDER BY s.name")
    List<Suppliers> findAllActiveOrdered();

    @Query("SELECT s FROM Suppliers s WHERE LOWER(s.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR LOWER(s.code) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<Suppliers> searchByNameOrCode(String searchTerm);

    List<Suppliers> findByCountry(String country);

    List<Suppliers> findByCity(String city);

    @Query("SELECT s FROM Suppliers s WHERE s.rating >= :minRating AND s.active = true ORDER BY s.rating DESC")
    List<Suppliers> findByMinRating(Integer minRating);
}
