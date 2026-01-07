package com.pos_onlineshop.hybrid.cartItem;

import com.pos_onlineshop.hybrid.cart.Cart;
import com.pos_onlineshop.hybrid.products.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    Optional<CartItem> findByCartAndProduct(Cart cart, Product product);

    void deleteByCart(Cart cart);
}