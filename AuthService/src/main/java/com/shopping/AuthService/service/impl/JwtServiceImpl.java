package com.shopping.AuthService.service.impl;

import com.shopping.AuthService.entity.User;
import com.shopping.AuthService.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.*;
import java.util.function.Function;

@Service
public class JwtServiceImpl implements JwtService {

    private final String secretKey;

    public JwtServiceImpl(@Value("${auth.jwt.secret}") String secretKey) {
        this.secretKey = secretKey;
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {

        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        SecretKey sk = Keys.hmacShaKeyFor(keyBytes);

        return Jwts.parser()
                .verifyWith(sk)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String generateToken(User user) {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        SecretKey sk = Keys.hmacShaKeyFor(keyBytes);

        Map<String, Object> inputClaims = new HashMap<>();
        inputClaims.put("email", user.getEmail());
        inputClaims.put("id", user.getId());
        inputClaims.put("roles", List.of(Boolean.TRUE.equals(user.getIsSeller()) ? "SELLER" : "BUYER"));

        return Jwts.builder()
                .issuer("auth-server")
                .subject(user.getId().toString())
                .claims(inputClaims)
                .audience().add("api-client").and()
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 48 * 60 * 60 * 1000))
                .id(UUID.randomUUID().toString())
                .signWith(sk)
                .compact();
    }

    public boolean validateToken(String token, String username) {
        final String extractedUsername = extractUsername(token);
        return (extractedUsername.equals(username) && !isTokenExpired(token));
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }
}
