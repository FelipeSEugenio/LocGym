package com.locgym.pagamento.infraestrutura;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Tópicos Kafka produzidos pelo módulo de pagamento (criados na subida pelo KafkaAdmin). */
@Configuration(proxyBeanMethods = false)
class TopicosPagamento {

    @Bean
    NewTopic topicoPagamentoGerado() {
        return TopicBuilder.name("pagamento.gerado").partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic topicoPagamentoRegistrado() {
        return TopicBuilder.name("pagamento.registrado").partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic topicoPagamentoAtrasado() {
        return TopicBuilder.name("pagamento.atrasado").partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic topicoPagamentoCancelado() {
        return TopicBuilder.name("pagamento.cancelado").partitions(3).replicas(1).build();
    }
}
