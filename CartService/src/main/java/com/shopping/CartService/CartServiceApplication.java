package com.shopping.CartService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Hello world!
 *
 */

@SpringBootApplication(scanBasePackages = "com.shopping")
public class CartServiceApplication
{
    public static void main( String[] args )
    {
        SpringApplication.run(CartServiceApplication.class, args);
    }
}
