package com.example.queueanalytics;

import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:analytics;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "spring.datasource.username=sa", "spring.datasource.password=", "analytics.collector-enabled=false"})
class H2DatabaseTest extends DatabaseContract {
}
