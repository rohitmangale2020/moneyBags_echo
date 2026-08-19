package com.training.platform.users.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Converts the retired PENDING_VERIFICATION value before Hibernate reads user records. */
@Configuration
class LegacyUserStatusMigration {
    private static final Logger log = LoggerFactory.getLogger(LegacyUserStatusMigration.class);

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    ApplicationRunner migratePendingVerificationUsers(JdbcTemplate jdbcTemplate) {
        return args -> {
            int migrated = jdbcTemplate.update("""
                    UPDATE MB_USERS
                    SET STATUS = 'ACTIVE'
                    WHERE STATUS = 'PENDING_VERIFICATION'
                    """);
            if (migrated > 0) log.info("Migrated {} legacy pending user status value(s) to ACTIVE", migrated);
        };
    }
}
