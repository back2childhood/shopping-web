package com.shopping.API_Gateway.config;

import com.shopping.API_Gateway.filter.AuthenticationFilter;
import org.springframework.cloud.gateway.filter.factory.SpringCloudCircuitBreakerFilterFactory;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator customRoutes(RouteLocatorBuilder builder, AuthenticationFilter authFilter) {
        return builder.routes()
                .route("account-service", r -> r.path("/api/accounts/**")
                        .filters(f -> f.filter(authFilter.apply(new AuthenticationFilter.Config()))
                                .circuitBreaker(config -> configureCircuitBreaker(
                                        config, "accountServiceCircuitBreaker")))
                        .uri("lb://ACCOUNT-SERVICE"))
                .route("item-service", r -> r.path("/api/items/**")
                        .filters(f -> f.filter(authFilter.apply(new AuthenticationFilter.Config()))
                                .circuitBreaker(config -> configureCircuitBreaker(
                                        config, "itemServiceCircuitBreaker")))
                        .uri("lb://ITEM-SERVICE"))
                .route("auth-service", r -> r.path("/api/auth/**")
                        .filters(f -> f.circuitBreaker(config -> configureCircuitBreaker(
                                config, "authServiceCircuitBreaker")))
                        .uri("lb://AUTH-SERVICE"))
                .route("order-service", r -> r.path("/api/orders/**")
                        .filters(f -> f.filter(authFilter.apply(new AuthenticationFilter.Config()))
                                .circuitBreaker(config -> configureCircuitBreaker(
                                        config, "orderServiceCircuitBreaker")))
                        .uri("lb://ORDER-SERVICE"))
                .build();
    }

    private static void configureCircuitBreaker(
            SpringCloudCircuitBreakerFilterFactory.Config config, String name) {
        config.setName(name)
                .setFallbackUri("forward:/fallback")
                .addStatusCode("INTERNAL_SERVER_ERROR")
                .addStatusCode("BAD_GATEWAY")
                .addStatusCode("SERVICE_UNAVAILABLE")
                .addStatusCode("GATEWAY_TIMEOUT");
    }

    @Bean
    public org.springframework.security.web.server.SecurityWebFilterChain securityWebFilterChain(
            org.springframework.security.config.web.server.ServerHttpSecurity http) {
        return http.csrf(org.springframework.security.config.web.server.ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange.anyExchange().permitAll())
                .build();
    }

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:3000"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return new CorsWebFilter(source);
    }
}
