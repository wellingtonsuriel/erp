package com.pos_onlineshop.hybrid.controllers;

import com.pos_onlineshop.hybrid.cart.Cart;
import com.pos_onlineshop.hybrid.cartItem.CartItem;
import com.pos_onlineshop.hybrid.dtos.AddToCartRequest;
import com.pos_onlineshop.hybrid.dtos.UpdateCartItemRequest;
import com.pos_onlineshop.hybrid.services.CartService;
import com.pos_onlineshop.hybrid.services.UserAccountService;
import com.pos_onlineshop.hybrid.userAccount.UserAccount;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@PreAuthorize("hasRole('USER')")
@CrossOrigin(origins = "*")
public class CartController {

    private final CartService cartService;
    private final UserAccountService userAccountService;

    @GetMapping
    public ResponseEntity<Cart> getCart(@AuthenticationPrincipal UserDetails userDetails) {
        UserAccount user = getUserAccount(userDetails);
        Cart cart = cartService.getOrCreateCart(user);
        return ResponseEntity.ok(cart);
    }

    @PostMapping("/items")
    public ResponseEntity<CartItem> addToCart(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody AddToCartRequest request) {
        UserAccount user = getUserAccount(userDetails);

        try {
            CartItem item = cartService.addToCart(
                    user,
                    request.getProductId(),
                    request.getQuantity()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(item);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/items/{itemId}")
    public ResponseEntity<Void> updateCartItem(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long itemId,
            @RequestBody UpdateCartItemRequest request) {
        UserAccount user = getUserAccount(userDetails);

        try {
            cartService.updateCartItemQuantity(user, itemId, request.getQuantity());
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<Void> removeFromCart(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long itemId) {
        UserAccount user = getUserAccount(userDetails);

        try {
            cartService.removeFromCart(user, itemId);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart(@AuthenticationPrincipal UserDetails userDetails) {
        UserAccount user = getUserAccount(userDetails);
        cartService.clearCart(user);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getCartSummary(
            @AuthenticationPrincipal UserDetails userDetails) {
        UserAccount user = getUserAccount(userDetails);
        Cart cart = cartService.getOrCreateCart(user);

        return ResponseEntity.ok(Map.of(
                "totalItems", cart.getTotalItems(),
                "totalAmount", cart.getTotalAmount(),
                "itemCount", cart.getCartItems().size()
        ));
    }

    @GetMapping("/abandoned")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Cart> getAbandonedCarts(@RequestParam(defaultValue = "24") int hours) {
        return cartService.findAbandonedCarts(hours);
    }

    private UserAccount getUserAccount(UserDetails userDetails) {
        return userAccountService.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }


}