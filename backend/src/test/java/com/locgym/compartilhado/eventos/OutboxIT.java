package com.locgym.compartilhado.eventos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import com.locgym.TestcontainersConfig;
import com.locgym.compartilhado.eventos.infraestrutura.OutboxRelay;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {
    "locgym.outbox.relay-habilitado=false",
    "spring.kafka.consumer.auto-offset-reset=earliest"
})
@Import({TestcontainersConfig.class, OutboxIT.TopicoDeTeste.class})
class OutboxIT {

    @TestConfiguration(proxyBeanMethods = false)
    static class TopicoDeTeste {
        @Bean
        NewTopic topicoTeste() {
            return new NewTopic(EventoTeste.TIPO, 1, (short) 1);
        }
    }

    @Autowired PublicadorEventos publicador;
    @Autowired OutboxRelay relay;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @Autowired ConsumerFactory<String, String> consumerFactory;
    @MockitoSpyBean KafkaTemplate<String, String> kafka;

    Consumer<String, String> consumidor;

    @BeforeEach
    void preparar() {
        jdbc.update("delete from outbox_evento");
        consumidor = consumerFactory.createConsumer("teste-" + UUID.randomUUID(), null);
        consumidor.subscribe(List.of(EventoTeste.TIPO));
    }

    @AfterEach
    void encerrar() {
        consumidor.close();
        reset(kafka);
    }

    private void publicarEmTransacao(EventoDominio... eventos) {
        tx.executeWithoutResult(s -> {
            for (EventoDominio e : eventos) publicador.publicar(e);
        });
    }

    /** Consome até encontrar `quantidade` mensagens que satisfaçam o filtro, ou até o prazo. */
    private List<ConsumerRecord<String, String>> consumir(
            Predicate<ConsumerRecord<String, String>> filtro, int quantidade, Duration prazo) {
        List<ConsumerRecord<String, String>> encontrados = new ArrayList<>();
        Instant limite = Instant.now().plus(prazo);
        while (encontrados.size() < quantidade && Instant.now().isBefore(limite)) {
            for (var r : consumidor.poll(Duration.ofMillis(200))) {
                if (filtro.test(r)) encontrados.add(r);
            }
        }
        return encontrados;
    }

    private Predicate<ConsumerRecord<String, String>> daAcademia(UUID academia) {
        return r -> academia.toString().equals(r.key());
    }

    private void falharEnvioQuando(Predicate<String> condicao) {
        doAnswer(inv -> {
            ProducerRecord<String, String> r = inv.getArgument(0);
            if (condicao.test(r.value())) {
                return CompletableFuture.failedFuture(new KafkaException("falha simulada"));
            }
            return inv.callRealMethod();
        }).when(kafka).send(any(ProducerRecord.class));
    }

    @Test
    void publicaEnvelopeCompleto() {
        UUID acad = UUID.randomUUID();
        publicarEmTransacao(new EventoTeste(acad, "oi"));

        assertThat(relay.publicarPendentes()).isEqualTo(1);

        var msgs = consumir(daAcademia(acad), 1, Duration.ofSeconds(15));
        assertThat(msgs).hasSize(1);
        JsonNode envelope = json.readTree(msgs.getFirst().value());
        assertThat(envelope.get("tipo").asString()).isEqualTo("teste.ocorrido");
        assertThat(envelope.get("versao").asInt()).isEqualTo(1);
        assertThat(envelope.get("academia_id").asString()).isEqualTo(acad.toString());
        assertThat(envelope.get("dados").get("mensagem").asString()).isEqualTo("oi");
        assertThat(envelope.get("dados").has("tipo")).isFalse();
        assertThat(envelope.get("dados").has("versao")).isFalse();
        assertThat(envelope.get("dados").has("academiaId")).isFalse();
        UUID eventoId = UUID.fromString(envelope.get("evento_id").asString());
        assertThat(Instant.parse(envelope.get("ocorrido_em").asString())).isNotNull();
        assertThat(new String(msgs.getFirst().headers().lastHeader("evento_id").value()))
                .isEqualTo(eventoId.toString());
        assertThat(jdbc.queryForObject(
                "select publicado_em is not null from outbox_evento where id = ?", Boolean.class, eventoId))
                .isTrue();
    }

    @Test
    void rollbackNaoPublica() {
        UUID acad = UUID.randomUUID();
        tx.executeWithoutResult(s -> {
            publicador.publicar(new EventoTeste(acad, "nunca"));
            s.setRollbackOnly();
        });

        assertThat(jdbc.queryForObject("select count(*) from outbox_evento", Integer.class)).isZero();
        assertThat(relay.publicarPendentes()).isZero();
        assertThat(consumir(daAcademia(acad), 1, Duration.ofSeconds(2))).isEmpty();
    }

    @Test
    void exigeTransacao() {
        assertThatThrownBy(() -> publicador.publicar(new EventoTeste(UUID.randomUUID(), "x")))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void naoPerdeEventoQuandoKafkaFalha() {
        UUID acad = UUID.randomUUID();
        publicarEmTransacao(new EventoTeste(acad, "resiliente"));
        falharEnvioQuando(valor -> true);

        assertThat(relay.publicarPendentes()).isZero();
        var linha = jdbc.queryForMap("select publicado_em, tentativas, ultimo_erro from outbox_evento");
        assertThat(linha.get("publicado_em")).isNull();
        assertThat(linha.get("tentativas")).isEqualTo(1);
        assertThat((String) linha.get("ultimo_erro")).isNotBlank();

        reset(kafka);
        assertThat(relay.publicarPendentes()).isEqualTo(1);
        assertThat(consumir(daAcademia(acad), 1, Duration.ofSeconds(15))).hasSize(1);
    }

    @Test
    void relaysConcorrentesNaoDuplicam() throws Exception {
        UUID acad = UUID.randomUUID();
        EventoDominio[] eventos = new EventoDominio[50];
        for (int i = 0; i < 50; i++) eventos[i] = new EventoTeste(acad, "e" + i);
        publicarEmTransacao(eventos);

        int total;
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(relay::publicarPendentes);
            var b = pool.submit(relay::publicarPendentes);
            total = a.get() + b.get();
        }
        int rodada;
        while ((rodada = relay.publicarPendentes()) > 0) total += rodada;

        assertThat(total).isEqualTo(50);
        var msgs = consumir(daAcademia(acad), 50, Duration.ofSeconds(20));
        assertThat(msgs.stream().map(r -> new String(r.headers().lastHeader("evento_id").value())).distinct())
                .hasSize(50);
        assertThat(consumir(daAcademia(acad), 1, Duration.ofSeconds(2))).as("sem duplicatas").isEmpty();
    }

    @Test
    void preservaOrdemAposFalha() {
        UUID acad = UUID.randomUUID();
        publicarEmTransacao(new EventoTeste(acad, "E1"), new EventoTeste(acad, "E2"), new EventoTeste(acad, "E3"));
        falharEnvioQuando(valor -> "E2".equals(json.readTree(valor).get("dados").get("mensagem").asString()));

        assertThat(relay.publicarPendentes()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from outbox_evento where publicado_em is null", Integer.class)).isEqualTo(2);

        reset(kafka);
        assertThat(relay.publicarPendentes()).isEqualTo(2);

        var msgs = consumir(daAcademia(acad), 3, Duration.ofSeconds(15));
        assertThat(msgs).extracting(r -> json.readTree(r.value()).get("dados").get("mensagem").asString())
                .containsExactly("E1", "E2", "E3");
    }

    @Test
    void chaveUsaEventoIdQuandoSemAcademia() {
        String marcador = "sem-academia-" + UUID.randomUUID();
        publicarEmTransacao(new EventoTeste(null, marcador));

        assertThat(relay.publicarPendentes()).isEqualTo(1);

        var msgs = consumir(r -> r.value().contains(marcador), 1, Duration.ofSeconds(15));
        assertThat(msgs).hasSize(1);
        JsonNode envelope = json.readTree(msgs.getFirst().value());
        assertThat(envelope.get("academia_id").isNull()).isTrue();
        assertThat(msgs.getFirst().key()).isEqualTo(envelope.get("evento_id").asString());
    }
}
