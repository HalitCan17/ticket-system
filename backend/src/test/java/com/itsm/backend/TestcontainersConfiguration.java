package com.itsm.backend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    // Keep in sync with infra/docker-compose.yml
    static final String POSTGRES_IMAGE = "postgres:18.6-alpine3.24";

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse(POSTGRES_IMAGE).asCompatibleSubstituteFor("postgres"));
    }
}
