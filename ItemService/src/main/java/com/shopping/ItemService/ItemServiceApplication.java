package com.shopping.ItemService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.cassandra.repository.config.EnableCassandraRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Hello world!
 *
 */
@SpringBootApplication
@EnableCassandraRepositories(basePackages = "com.shopping.ItemService.dao")
@EnableJpaRepositories(basePackages = "com.shopping.ItemService.dao")
@EnableScheduling
public class ItemServiceApplication
{
    public static void main( String[] args )
    {
        SpringApplication.run(ItemServiceApplication.class, args);
    }
}
