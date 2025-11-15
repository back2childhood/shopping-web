package com.shopping.API_Gateway.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

@Component
public class JwtUtil {

    @Value("${auth.jwt.secret}") // Inject the secret key
    private String secretKey;  // Non-static variable to receive value

    private static String baseString; // Static variable to be used everywhere

    @PostConstruct // Runs after Spring initializes the bean
    public void init() {
        baseString = secretKey; // Assign injected value to static variable
    }

    public static Claims extractAllClaims(String token) {
        byte[] keyBytes = Decoders.BASE64.decode(baseString);
        SecretKey sk = Keys.hmacShaKeyFor(keyBytes);

        return Jwts.parser()
                .verifyWith(sk)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public static String getEmailFromToken(String token) {

//        System.out.println(token);
        if(token == null || !token.startsWith("Bearer ")){
            return null;
        }

        return extractAllClaims(token).get("email", String.class);
    }

    public static boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
