package com.shopping.CartService.entity;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.redis.core.RedisHash;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder
@RedisHash("cart")
public class Cart implements Serializable {
    @Id
    private Long userId;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<CartItem> items = new ArrayList<>();

    private double totalPrice;

    public <E> Cart(Long userId, ArrayList<E> es) {
    }

    public <E> Cart(Long userId, ArrayList<E> es, int i, long l) {
    }
}
