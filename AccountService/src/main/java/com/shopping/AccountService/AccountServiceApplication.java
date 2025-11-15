package com.shopping.AccountService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.shopping")
public class AccountServiceApplication
{
    public static void main( String[] args )
    {
        SpringApplication.run(AccountServiceApplication.class, args);
    }
}
