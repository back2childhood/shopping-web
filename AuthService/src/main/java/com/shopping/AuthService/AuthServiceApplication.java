package com.shopping.AuthService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Hello world!
 *
 */

@SpringBootApplication(scanBasePackages = "com.shopping")
public class AuthServiceApplication
{
    public static void main( String[] args )
    {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
