package com.locgym;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Containers compartilhados por todos os testes de integração (mesmas versões do docker-compose). */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    public static final String IMAGEM_POSTGRES = "postgres:17-alpine";
    public static final String IMAGEM_KAFKA = "apache/kafka:4.3.2";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse(IMAGEM_POSTGRES));
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer(DockerImageName.parse(IMAGEM_KAFKA));
    }
}
