package com.pos_onlineshop.hybrid.cart;

import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByUser(UserAccount user);

    @Query("SELECT c FROM Cart c JOIN FETCH c.cartItems WHERE c.user.id = :userId")
    Optional<Cart> findByUserIdWithItems(Long userId);

    @Query("SELECT c FROM Cart c WHERE c.updatedAt < :dateTime")
    List<Cart> findAbandonedCarts(LocalDateTime dateTime);

    void deleteByUser(UserAccount user);
}