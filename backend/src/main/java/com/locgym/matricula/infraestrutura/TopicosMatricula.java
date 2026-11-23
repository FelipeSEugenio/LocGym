package com.locgym.matricula.infraestrutura;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Tópicos Kafka produzidos pelo módulo de matrícula (criados na subida pelo KafkaAdmin). */
@Configuration(proxyBeanMethods = false)
class TopicosMatricula {

    private static NewTopic topico(String nome) {
        return TopicBuilder.name(nome).partitions(3).replicas(1).build();
    }

    @Bean NewTopic topicoMatriculaCriada() { return topico("matricula.criada"); }
    @Bean NewTopic topicoMatriculaRenovada() { return topico("matricula.renovada"); }
    @Bean NewTopic topicoMatriculaVencida() { return topico("matricula.vencida"); }
    @Bean NewTopic topicoMatriculaSuspensa() { return topico("matricula.suspensa"); }
    @Bean NewTopic topicoMatriculaReativada() { return topico("matricula.reativada"); }
    @Bean NewTopic topicoMatriculaCancelada() { return topico("matricula.cancelada"); }
}
