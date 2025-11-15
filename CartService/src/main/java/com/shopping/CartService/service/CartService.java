package com.shopping.CartService.service;

import com.shopping.CartService.entity.Cart;
import com.shopping.CartService.entity.CartItem;
import com.shopping.CartService.payload.CartDto;

public interface CartService {
    boolean addItem(Long userId, CartItem item);
    boolean removeItem(Long userId, String itemId);
    boolean updateQuantity(Long userId, String itemId, int quantity);
    CartDto getCart(Long userId);
    boolean clearCart(Long userId);
}
