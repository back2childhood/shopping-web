package com.shopping.ItemService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.cassandra.repository.config.EnableCassandraRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Hello world!
 *
 */
@SpringBootApplication
@EnableCassandraRepositories(basePackages = "com.shopping.ItemService.dao")
@EnableJpaRepositories(basePackages = "com.shopping.ItemService.inventory")
public class ItemServiceApplication
{
    public static void main( String[] args )
    {
        SpringApplication.run(ItemServiceApplication.class, args);
    }
}
