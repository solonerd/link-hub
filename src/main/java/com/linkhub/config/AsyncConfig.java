package com.linkhub.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer; // 🌟 新增导入

import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Configuration
public class AsyncConfig implements AsyncConfigurer, WebMvcConfigurer { // 🌟 顺便实现 WebMvcConfigurer

    private final AtomicLong rejectedCount = new AtomicLong();

    @Override
    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);           // 核心线程数
        executor.setMaxPoolSize(30);           // 最大线程数
        executor.setQueueCapacity(500);        // 队列容量
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("async-task-");
        executor.setRejectedExecutionHandler((r, e) -> {
            long total = rejectedCount.incrementAndGet();
            log.warn("任务被丢弃，累计 {} 次，队列已满", total);
        });
        executor.initialize();
        return executor;
    }

    // 🌟 核心新增：把你写的线程池，配置给 Spring MVC 的流式请求使用！
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        // 强行让 Web 异步使用我们的 taskExecutor
        configurer.setTaskExecutor(getAsyncExecutor());
        // 设置默认超时时间：60秒（大模型最长推流时间，防止连接一直挂着被网关掐断）
        configurer.setDefaultTimeout(60000);
    }
}