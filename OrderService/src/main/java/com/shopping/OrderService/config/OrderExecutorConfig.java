package com.shopping.OrderService.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class OrderExecutorConfig {

    @Bean(name = "orderItemLookupExecutor")
    ThreadPoolTaskExecutor orderItemLookupExecutor(
            @Value("${app.order.item-lookup-executor.core-pool-size:8}") int corePoolSize,
            @Value("${app.order.item-lookup-executor.max-pool-size:32}") int maxPoolSize,
            @Value("${app.order.item-lookup-executor.queue-capacity:200}") int queueCapacity,
            @Value("${app.order.item-lookup-executor.keep-alive-seconds:60}") int keepAliveSeconds) {
        if (corePoolSize <= 0 || maxPoolSize < corePoolSize || queueCapacity < 0 || keepAliveSeconds < 0) {
            throw new IllegalArgumentException("Invalid order item lookup executor configuration");
        }

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setThreadNamePrefix("order-item-lookup-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }
}
