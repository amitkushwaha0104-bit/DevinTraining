package com.capstone.fraud;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies Flyway migrations against a real MySQL 8 instance. Skipped
 * automatically when Docker is unavailable (e.g. CI without docker).
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("mysql-it")
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "capstone.processor-mode=rules"
})
class MySqlFlywayTests {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("fraudcapstone")
            .withUsername("fraud")
            .withPassword("fraud");

    @Autowired
    DataSource dataSource;

    @Test
    void flywayMigratedSchema() throws Exception {
        try (Connection c = dataSource.getConnection();
             var rs = c.getMetaData().getTables(null, null, "fraud_case", null)) {
            assertTrue(rs.next(), "fraud_case table should exist after Flyway migration");
        }
        try (Connection c = dataSource.getConnection();
             var rs = c.getMetaData().getTables(null, null, "audit_event", null)) {
            assertTrue(rs.next(), "audit_event table should exist after Flyway migration");
        }
    }
}
