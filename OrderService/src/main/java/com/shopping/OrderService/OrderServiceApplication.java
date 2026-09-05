package com.shopping.OrderService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Hello world!
 *
 */

@EnableFeignClients(basePackages = "com.shopping.OrderService.client")
@EnableCaching
@SpringBootApplication
public class OrderServiceApplication
{
    public static void main( String[] args )
    {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
