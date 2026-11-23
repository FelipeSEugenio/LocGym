package com.locgym.matricula.infraestrutura;

import com.locgym.matricula.aplicacao.RotinaDiaria;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Dispara a rotina diária às 00:05 de São Paulo, em uma instância por vez (ShedLock na tabela {@code shedlock}).
 * Desligável com {@code locgym.rotina-diaria.habilitada=false} (testes).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "30m")
@ConditionalOnProperty(name = "locgym.rotina-diaria.habilitada", havingValue = "true", matchIfMissing = true)
class AgendadorRotinaDiaria {

    private final RotinaDiaria rotina;

    AgendadorRotinaDiaria(RotinaDiaria rotina) {
        this.rotina = rotina;
    }

    @Bean
    LockProvider lockProvider(JdbcTemplate jdbc) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(jdbc).usingDbTime().build());
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "America/Sao_Paulo")
    @SchedulerLock(name = "rotina-diaria", lockAtMostFor = "30m")
    void executar() {
        rotina.executar();
    }
}
