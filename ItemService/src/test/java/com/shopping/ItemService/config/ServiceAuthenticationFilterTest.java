package com.shopping.ItemService.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceAuthenticationFilterTest {
    private static final String JWT_SECRET =
            "UnI6T1JQdWdJYW1lcDJjdmlOTThMc0ZpbDJwY3N3SFJ6aEhTS2FKQ3BaYz0=";
    private static final String INTERNAL_TOKEN = "test-order-service-token";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesSellerRoleFromJwt() throws Exception {
        String token = Jwts.builder()
                .subject("42")
                .claim("roles", List.of("SELLER"))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(JWT_SECRET)))
                .compact();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);

        new ServiceAuthenticationFilter(INTERNAL_TOKEN, JWT_SECRET).doFilter(
                request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("42");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_SELLER");
    }

    @Test
    void authenticatesOrderServiceForStockOperations() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Internal-Service-Token", INTERNAL_TOKEN);

        new ServiceAuthenticationFilter(INTERNAL_TOKEN, JWT_SECRET).doFilter(
                request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ORDER_SERVICE");
    }
}
