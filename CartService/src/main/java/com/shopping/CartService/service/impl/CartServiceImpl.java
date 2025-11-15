package com.shopping.CartService.service.impl;

import com.shopping.CartService.dao.CartJpaRepository;
import com.shopping.CartService.entity.Cart;
import com.shopping.CartService.entity.CartItem;
import com.shopping.CartService.payload.CartDto;
import com.shopping.CartService.service.CartService;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
//@RequiredArgsConstructor
@Slf4j
public class CartServiceImpl implements CartService {

    private RedisTemplate<String, Object> redis;
    private StringRedisTemplate str;
    private CartJpaRepository repo;
    private ModelMapper modelMapper;

    @Autowired
    public CartServiceImpl(RedisTemplate<String, Object> redis, StringRedisTemplate str, CartJpaRepository repo, ModelMapper modelMapper) {
        this.redis = redis;
        this.str = str;
        this.repo = repo;
        this.modelMapper = modelMapper;
    }

    private static final String NIL = "NIL";

    private String key(Long userId) {
        return "cart:user:" + userId;
    }

    @Override
    public boolean addItem(Long userId, CartItem item) {
        String k = key(userId);

        // 1️⃣ Fetch current cart from Redis or DB
        CartDto cart = getCart(userId);
        if (cart == null) {
            cart = new CartDto();
            cart.setUserId(userId);
            cart.setItems(new ArrayList<>());
        }

        // 2️⃣ Update or insert item
        cart.getItems().removeIf(i -> i.getItemId().equals(item.getItemId()));
        cart.getItems().add(modelMapper.map(item, CartDto.CartItemDto.class));
        recalcTotal(cart);

        // 3️⃣ Save to DB
        repo.save(modelMapper.map(cart, Cart.class));

        // 4️⃣ Invalidate cache (double delete)
        redis.delete(k);
        CompletableFuture.runAsync(() -> {
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            redis.delete(k);
        });

        return true;
    }

    @Override
    public boolean removeItem(Long userId, String itemId) {
        String k = key(userId);
        CartDto cart = getCart(userId);
        if (cart == null) return false;

        boolean removed = cart.getItems().removeIf(i -> i.getItemId().equals(itemId));
        if (!removed) return false;

        recalcTotal(cart);
        repo.save(modelMapper.map(cart, Cart.class));

        // Invalidate cache
        redis.delete(k);
        CompletableFuture.runAsync(() -> {
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            redis.delete(k);
        });
        return true;
    }

    @Override
    public boolean updateQuantity(Long userId, String itemId, int quantity) {
        String k = key(userId);
        CartDto cart = getCart(userId);
        if (cart == null) return false;

        for (CartDto.CartItemDto item : cart.getItems()) {
            if (item.getItemId().equals(itemId)) {
                item.setQuantity(quantity);
                break;
            }
        }

        recalcTotal(cart);
        repo.save(modelMapper.map(cart, Cart.class));

        redis.delete(k);
        CompletableFuture.runAsync(() -> {
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            redis.delete(k);
        });
        return true;
    }

    @Override
    public CartDto getCart(Long userId) {
        String k = key(userId);
        System.out.println(userId);

        // 1️⃣ Check cache
        Object v = redis.opsForValue().get(k);
        System.out.println(v);
        if (v != null) return NIL.equals(v) ? null : (CartDto) v;

        System.out.println("getCart from db");

        // 2️⃣ Check bloom filter
//        Boolean mightExist = str.opsForSet().isMember("cart:bloom:ids", String.valueOf(userId));
//        System.out.println(mightExist);
//        if (Boolean.FALSE.equals(mightExist)) {
//            redis.opsForValue().set(k, NIL, 60, TimeUnit.SECONDS);
//            return null;
//        }

        // 3️⃣ Query DB
        Cart db = repo.findById(userId).orElse(null);
//        System.out.println(db);

        CartDto res = modelMapper.map(db, CartDto.class);

        if (db == null) {
            redis.opsForValue().set(k, NIL, 60, TimeUnit.SECONDS);
            return null;
        }

        // 4️⃣ Cache it
        long jitter = ThreadLocalRandom.current().nextLong(1, 6);
        redis.opsForValue().set(k, res, 10 + jitter, TimeUnit.MINUTES);



        return res;
    }

    @Override
    public boolean clearCart(Long userId) {
        String k = key(userId);
        repo.deleteById(userId);
        redis.delete(k);
        CompletableFuture.runAsync(() -> {
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            redis.delete(k);
        });
        return true;
    }

    private void recalcTotal(CartDto cart) {
        double total = cart.getItems().stream()
                .mapToDouble(i -> i.getQuantity() * i.getPrice())
                .sum();
        cart.setTotalPrice(total);
    }
}
