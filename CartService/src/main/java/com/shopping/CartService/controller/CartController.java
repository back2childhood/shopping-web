package com.shopping.CartService.controller;

import com.shopping.CartService.entity.Cart;
import com.shopping.CartService.entity.CartItem;
import com.shopping.CartService.payload.CartDto;
import com.shopping.CartService.service.CartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

@RestController
@RequestMapping("/api/cart")
//@RequiredArgsConstructor
public class CartController {

    private CartService cartService;

    @Autowired
    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping("/{userId}/add")
    public ResponseEntity<String> addItem(@PathVariable Long userId, @RequestBody CartItem item) {
        cartService.addItem(userId, item);
        return ResponseEntity.ok("Item added to cart");
    }

    @PutMapping("/{userId}/update/{itemId}")
    public ResponseEntity<String> updateQuantity(@PathVariable Long userId, @PathVariable String itemId, @RequestParam int quantity) {
        cartService.updateQuantity(userId, itemId, quantity);
        return ResponseEntity.ok("Quantity updated");
    }

    @DeleteMapping("/{userId}/remove/{itemId}")
    public ResponseEntity<String> removeItem(@PathVariable Long userId, @PathVariable String itemId) {
        cartService.removeItem(userId, itemId);
        return ResponseEntity.ok("Item removed");
    }

    @GetMapping("/{userId}")
    public ResponseEntity<?> getCart(@PathVariable Long userId) {
        CartDto cart = cartService.getCart(userId);
        if (cart == null) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "Cart is empty");
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.ok(cart);
    }

    @DeleteMapping("/{userId}/clear")
    public ResponseEntity<String> clearCart(@PathVariable Long userId) {
        cartService.clearCart(userId);
        return ResponseEntity.ok("Cart cleared");
    }
}
