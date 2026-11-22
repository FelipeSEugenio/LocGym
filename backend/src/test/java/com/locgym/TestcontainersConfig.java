package com.locgym;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Containers compartilhados por todos os testes de integração (mesmas versões do docker-compose).
 *
 * <p>Como em produção, as migrations rodam com o dono do schema (superusuário do container) e a
 * aplicação conecta como {@code locgym_app} — sem isso o RLS não estaria sendo testado.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    public static final String IMAGEM_POSTGRES = "postgres:17-alpine";
    public static final String IMAGEM_KAFKA = "apache/kafka:4.3.2";
    public static final String USUARIO_APP = "locgym_app";
    public static final String SENHA_APP = "locgym_app";

    /** Acesso ao banco como dono do schema (ignora RLS): só para preparar e conferir dados nos testes. */
    public record BancoSuperusuario(JdbcTemplate jdbc) {}

    @Bean
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse(IMAGEM_POSTGRES));
    }

    @Bean
    DynamicPropertyRegistrar propriedadesDoBanco(PostgreSQLContainer postgres) {
        return registro -> {
            registro.add("spring.datasource.url", postgres::getJdbcUrl);
            registro.add("spring.datasource.username", () -> USUARIO_APP);
            registro.add("spring.datasource.password", () -> SENHA_APP);
            registro.add("spring.flyway.url", postgres::getJdbcUrl);
            registro.add("spring.flyway.user", postgres::getUsername);
            registro.add("spring.flyway.password", postgres::getPassword);
            registro.add("spring.flyway.placeholders.app_usuario", () -> USUARIO_APP);
            registro.add("spring.flyway.placeholders.app_senha", () -> SENHA_APP);
        };
    }

    @Bean
    BancoSuperusuario bancoSuperusuario(PostgreSQLContainer postgres) {
        var ds = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        return new BancoSuperusuario(new JdbcTemplate(ds));
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer(DockerImageName.parse(IMAGEM_KAFKA));
    }
}
