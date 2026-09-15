package com.shopping.AuthService.service.impl;

import com.shopping.AuthService.entity.User;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceImplTest {
    private static final String SECRET =
            "UnI6T1JQdWdJYW1lcDJjdmlOTThMc0ZpbDJwY3N3SFJ6aEhTS2FKQ3BaYz0=";

    @Test
    void sellerTokenCarriesTheGlobalIdentityAndBrowserClaims() {
        JwtServiceImpl service = new JwtServiceImpl(SECRET);
        User user = new User(42L, "seller@example.com", "encoded-password", true);

        String token = service.generateToken(user);
        Claims claims = service.extractClaim(token, value -> value);

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("id", Number.class).longValue()).isEqualTo(42L);
        assertThat(claims.get("email", String.class)).isEqualTo("seller@example.com");
        assertThat(claims.get("roles")).isEqualTo(java.util.List.of("SELLER"));
        assertThat(claims.getIssuer()).isEqualTo("auth-server");
        assertThat(claims.getAudience()).containsExactly("api-client");
    }
}
