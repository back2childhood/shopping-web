package com.shopping.AuthService.service;

import com.shopping.AuthService.entity.User;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Service;

import java.util.function.Function;

public interface JwtService {

    public String extractUsername(String token);

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver);

    public String generateToken(User user);

    public boolean validateToken(String token, String username);

}
