package com.shopping.ItemService.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Component
public class ServiceAuthenticationFilter extends OncePerRequestFilter {
    private final byte[] internalServiceToken;
    private final String jwtSecret;

    public ServiceAuthenticationFilter(
            @Value("${security.internal-service-token}") String internalServiceToken,
            @Value("${auth.jwt.secret}") String jwtSecret) {
        this.internalServiceToken = internalServiceToken.getBytes(StandardCharsets.UTF_8);
        this.jwtSecret = jwtSecret;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticateInternalService(request);
        }
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticateUser(request);
        }
        chain.doFilter(request, response);
    }

    private void authenticateInternalService(HttpServletRequest request) {
        String suppliedToken = request.getHeader("X-Internal-Service-Token");
        if (suppliedToken != null && MessageDigest.isEqual(
                internalServiceToken, suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            setAuthentication("order-service", List.of("ROLE_ORDER_SERVICE"));
        }
    }

    private void authenticateUser(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return;
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret)))
                    .build()
                    .parseSignedClaims(authorization.substring(7))
                    .getPayload();
            List<?> roles = claims.get("roles", List.class);
            List<String> authorities = roles == null ? List.of() : roles.stream()
                    .map(String::valueOf)
                    .map(role -> "ROLE_" + role)
                    .toList();
            setAuthentication(claims.getSubject(), authorities);
        } catch (RuntimeException ignored) {
            // Spring Security returns 401 or 403 for protected endpoints.
        }
    }

    private void setAuthentication(String principal, List<String> authorities) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                authorities.stream().map(SimpleGrantedAuthority::new).toList());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
