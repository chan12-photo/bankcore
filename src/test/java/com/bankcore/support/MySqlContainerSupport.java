package com.bankcore.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

public abstract class MySqlContainerSupport {

    @Container
    @ServiceConnection
    protected static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("bankcore_test")
            .withUsername("bankcore")
            .withPassword("bankcore_password");
}
