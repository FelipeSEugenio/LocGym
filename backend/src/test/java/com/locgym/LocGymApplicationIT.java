package com.locgym;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "management.endpoint.health.group.readiness.show-components=always",
            "management.endpoint.health.group.liveness.show-components=always"
        })
@Import(TestcontainersConfig.class)
class LocGymApplicationIT {

    @LocalServerPort int porta;
    @Autowired JdbcTemplate jdbc;
    RestClient http;

    @BeforeEach
    void configurarCliente() {
        http = RestClient.builder()
                .baseUrl("http://localhost:" + porta)
                .defaultStatusHandler(status -> true, (req, res) -> {})
                .build();
    }

    private ResponseEntity<String> get(String caminho) {
        return http.get().uri(caminho).retrieve().toEntity(String.class);
    }

    @Test
    void healthEstaUp() {
        var r = get("/actuator/health");
        assertThat(r.getStatusCode().value()).isEqualTo(200);
        assertThat(r.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void livenessEReadinessExpostos() {
        assertThat(get("/actuator/health/liveness").getStatusCode().value()).isEqualTo(200);
        assertThat(get("/actuator/health/readiness").getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void readinessIncluiDbMasNaoKafka() {
        var corpo = get("/actuator/health/readiness").getBody();
        assertThat(corpo).contains("\"db\"").doesNotContainIgnoringCase("kafka");
    }

    @Test
    void livenessNaoIncluiDependencias() {
        var corpo = get("/actuator/health/liveness").getBody();
        assertThat(corpo).doesNotContain("\"db\"").doesNotContainIgnoringCase("kafka");
    }

    @Test
    void migrationsAplicadas() {
        assertThat(jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success", Integer.class))
                .isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select to_regclass('public.outbox_evento') is not null", Boolean.class))
                .isTrue();
    }

    @Test
    void openApiDisponivel() {
        assertThat(get("/v3/api-docs").getStatusCode().value()).isEqualTo(200);
    }
}
