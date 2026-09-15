package com.shopping.OrderService.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class ItemClientConfig {
    @Bean
    RequestInterceptor internalServiceTokenInterceptor(
            @Value("${security.internal-service-token}") String internalServiceToken) {
        return template -> template.header("X-Internal-Service-Token", internalServiceToken);
    }
}
