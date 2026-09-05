package com.shopping.AuthService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Hello world!
 *
 */

@SpringBootApplication
@EnableFeignClients
public class AuthServiceApplication
{
    public static void main( String[] args )
    {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
