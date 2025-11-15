package com.shopping.ItemService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Hello world!
 *
 */
@SpringBootApplication(scanBasePackages = "com.shopping", exclude = {org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class})
public class ItemServiceApplication
{
    public static void main( String[] args )
    {
        SpringApplication.run(ItemServiceApplication.class, args);
    }
}
