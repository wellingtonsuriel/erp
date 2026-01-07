package com.pos_onlineshop.hybrid.services;

import com.pos_onlineshop.hybrid.cart.Cart;
import com.pos_onlineshop.hybrid.cart.CartRepository;
import com.pos_onlineshop.hybrid.cartItem.CartItem;
import com.pos_onlineshop.hybrid.cartItem.CartItemRepository;
import com.pos_onlineshop.hybrid.products.Product;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductService productService;

    public Cart createCart(UserAccount user) {
        Cart cart = Cart.builder()
                .user(user)
                .build();

        return cartRepository.save(cart);
    }

    public Optional<Cart> getCartByUser(UserAccount user) {
        return cartRepository.findByUserIdWithItems(user.getId());
    }

    public Cart getOrCreateCart(UserAccount user) {
        return getCartByUser(user).orElseGet(() -> createCart(user));
    }

    @Transactional
    public CartItem addToCart(UserAccount user, Long productId, Integer quantity) {
        Cart cart = getOrCreateCart(user);
        Product product = productService.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found: " + productId));

        Optional<CartItem> existingItem = cartItemRepository.findByCartAndProduct(cart, product);

        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            item.incrementQuantity(quantity);
            return cartItemRepository.save(item);
        } else {
            CartItem newItem = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .quantity(quantity)
                    .build();

            cart.addItem(newItem);
            cartRepository.save(cart);
            return newItem;
        }
    }

    @Transactional
    public void removeFromCart(UserAccount user, Long cartItemId) {
        Cart cart = getCartByUser(user)
                .orElseThrow(() -> new RuntimeException("Cart not found"));

        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new RuntimeException("Cart item not found"));

        if (!item.getCart().getId().equals(cart.getId())) {
            throw new RuntimeException("Cart item does not belong to user's cart");
        }

        cart.removeItem(item);
        cartItemRepository.delete(item);
        cartRepository.save(cart);
    }

    @Transactional
    public void updateCartItemQuantity(UserAccount user, Long cartItemId, Integer newQuantity) {
        if (newQuantity <= 0) {
            removeFromCart(user, cartItemId);
            return;
        }

        Cart cart = getCartByUser(user)
                .orElseThrow(() -> new RuntimeException("Cart not found"));

        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new RuntimeException("Cart item not found"));

        if (!item.getCart().getId().equals(cart.getId())) {
            throw new RuntimeException("Cart item does not belong to user's cart");
        }

        item.setQuantity(newQuantity);
        cartItemRepository.save(item);
    }

    @Transactional
    public void clearCart(UserAccount user) {
        Cart cart = getCartByUser(user)
                .orElseThrow(() -> new RuntimeException("Cart not found"));

        cart.clear();
        cartItemRepository.deleteByCart(cart);
        cartRepository.save(cart);
    }

    public List<Cart> findAbandonedCarts(int hours) {
        LocalDateTime threshold = LocalDateTime.now().minusHours(hours);
        return cartRepository.findAbandonedCarts(threshold);
    }

    @Transactional
    public void deleteCart(UserAccount user) {
        cartRepository.deleteByUser(user);
    }
}