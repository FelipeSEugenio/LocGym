package com.locgym.compartilhado.tenancy;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.cfg.MultiTenancySettings;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
class TenancyConfig {

    @Bean
    HibernatePropertiesCustomizer resolvedorDeTenant() {
        return props -> props.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, new ResolvedorTenant());
    }

    @Bean
    PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
        var gerenciador = new GerenciadorTransacaoComTenant();
        gerenciador.setEntityManagerFactory(emf);
        return gerenciador;
    }
}
