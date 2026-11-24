package com.locgym.frequencia.infraestrutura;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Tópicos Kafka produzidos pelo módulo de frequência (criados na subida pelo KafkaAdmin). */
@Configuration(proxyBeanMethods = false)
class TopicosFrequencia {

    private static NewTopic topico(String nome) {
        return TopicBuilder.name(nome).partitions(3).replicas(1).build();
    }

    @Bean NewTopic topicoCheckinRegistrado() { return topico("checkin.registrado"); }
    @Bean NewTopic topicoCheckinCorrigido() { return topico("checkin.corrigido"); }
    @Bean NewTopic topicoCheckinExcluido() { return topico("checkin.excluido"); }
}
