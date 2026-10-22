package com.example.queueanalytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class QueueAnalyticsApplication {
    public static void main(String[] args) {
        SpringApplication.run(QueueAnalyticsApplication.class, args);
    }
}
