package com.shopping.AuthService.service.impl;

import com.shopping.AuthService.client.AccountClient;
import com.shopping.AuthService.dao.UserRepository;
import com.shopping.AuthService.entity.User;
import com.shopping.AuthService.payload.AccountResponseDto;
import com.shopping.AuthService.payload.AuthResponse;
import com.shopping.AuthService.payload.RegisterRequest;
import com.shopping.AuthService.service.JwtService;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AuthServiceImplTest {

    @Test
    void registerUsesAccountServiceIdAsTheAuthUserId() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("  SELLER@example.com ");
        request.setUsername("Seller");
        request.setPassword("password123");
        request.setIsSeller(true);

        AccountResponseDto account = new AccountResponseDto();
        account.setId(42L);
        account.setEmail("seller@example.com");
        account.setUsername("Seller");
        account.setIsSeller(true);

        AtomicReference<User> savedUser = new AtomicReference<>();
        UserRepository repository = proxy(UserRepository.class, (method, args) -> switch (method) {
            case "findByEmail" -> Optional.empty();
            case "save" -> {
                savedUser.set((User) args[0]);
                yield args[0];
            }
            default -> null;
        });
        PasswordEncoder passwordEncoder = proxy(PasswordEncoder.class, (method, args) ->
                method.equals("encode") ? "encoded-password" : null);
        JwtService jwtService = proxy(JwtService.class, (method, args) ->
                method.equals("generateToken") ? "jwt-token" : null);
        AccountClient accountClient = proxy(AccountClient.class, (method, args) ->
                method.equals("createAccount") ? account : null);

        AuthServiceImpl service = new AuthServiceImpl(
                repository, passwordEncoder, jwtService, accountClient, new ModelMapper());
        AuthResponse response = service.register(request);

        assertThat(response.getUserId()).isEqualTo(42L);
        assertThat(response.getEmail()).isEqualTo("seller@example.com");
        assertThat(response.getRole()).isEqualTo("SELLER");
        assertThat(response.getIsSeller()).isTrue();
        assertThat(savedUser.get().getId()).isEqualTo(42L);
        assertThat(savedUser.get().getEmail()).isEqualTo("seller@example.com");
        assertThat(savedUser.get().getPassword()).isEqualTo("encoded-password");
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Stub stub) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> stub.invoke(method.getName(), args));
    }

    @FunctionalInterface
    private interface Stub {
        Object invoke(String method, Object[] args);
    }
}
