package com.shopping.ItemService.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ServiceAuthenticationFilter authenticationFilter)
            throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/items/*/increase-stock").hasRole("ORDER_SERVICE")
                        .requestMatchers(HttpMethod.POST, "/api/items/*/decrease-stock").hasRole("ORDER_SERVICE")
                        .requestMatchers(HttpMethod.POST, "/api/items").hasRole("SELLER")
                        .requestMatchers(HttpMethod.PUT, "/api/items/**").hasRole("SELLER")
                        .requestMatchers(HttpMethod.DELETE, "/api/items/**").hasRole("SELLER")
                        .requestMatchers(HttpMethod.GET, "/api/items/**").authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(authenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
