package com.example.queueanalytics;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

@EnabledIfEnvironmentVariable(named = "TEST_MYSQL_URL", matches = ".+")
@SpringBootTest(properties = {"spring.datasource.url=${TEST_MYSQL_URL}", "spring.datasource.username=${TEST_MYSQL_USERNAME:root}", "spring.datasource.password=${TEST_MYSQL_PASSWORD:}", "analytics.collector-enabled=false"})
class MySqlDatabaseTest extends DatabaseContract {
}
