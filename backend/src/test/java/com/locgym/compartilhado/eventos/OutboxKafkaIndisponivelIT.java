package com.locgym.compartilhado.eventos;

import static org.assertj.core.api.Assertions.assertThat;

import com.locgym.TestcontainersConfig;
import com.locgym.compartilhado.eventos.infraestrutura.OutboxRelay;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Kafka realmente fora do ar (container pausado): o ciclo do relay deve terminar em tempo limitado,
 * sem segurar transação, conexão e lock por ~60 s, e o evento deve continuar pendente.
 */
@SpringBootTest(properties = "locgym.outbox.relay-habilitado=false")
@Import(TestcontainersConfig.class)
class OutboxKafkaIndisponivelIT {

    @Autowired PublicadorEventos publicador;
    @Autowired OutboxRelay relay;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;
    @Autowired KafkaContainer kafka;

    @Test
    void cicloDoRelayTerminaRapidoComKafkaFora() {
        jdbc.update("delete from outbox_evento");
        UUID acad = UUID.randomUUID();
        tx.executeWithoutResult(s ->
                publicador.publicar(new EventoDinamico("teste.indisponivel." + UUID.randomUUID(), acad)));

        var docker = kafka.getDockerClient();
        docker.pauseContainerCmd(kafka.getContainerId()).exec();
        try {
            long inicio = System.nanoTime();
            int publicados = relay.publicarPendentes();
            Duration duracao = Duration.ofNanos(System.nanoTime() - inicio);

            assertThat(publicados).isZero();
            assertThat(duracao).isLessThan(Duration.ofSeconds(15));
            var linha = jdbc.queryForMap("select publicado_em, tentativas from outbox_evento");
            assertThat(linha.get("publicado_em")).isNull();
            assertThat(linha.get("tentativas")).isEqualTo(1);
        } finally {
            docker.unpauseContainerCmd(kafka.getContainerId()).exec();
        }
    }
}
