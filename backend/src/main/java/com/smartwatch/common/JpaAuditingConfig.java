package com.smartwatch.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Instant;
import java.util.Optional;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "utcInstantDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    public DateTimeProvider utcInstantDateTimeProvider() {
        return () -> Optional.of(Instant.now());
    }
}
