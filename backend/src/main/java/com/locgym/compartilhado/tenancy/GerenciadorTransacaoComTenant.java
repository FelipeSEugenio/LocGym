package com.locgym.compartilhado.tenancy;

import jakarta.persistence.EntityManager;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Ao iniciar cada transação, informa ao PostgreSQL a academia ativa ({@code app.academia_id}, escopo da
 * transação) — é o valor que as policies de RLS usam. Sem contexto, nada é definido e o RLS não mostra nada.
 */
public class GerenciadorTransacaoComTenant extends JpaTransactionManager {

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        TenantContext.atual().ifPresent(contexto -> {
            var holder = (EntityManagerHolder) TransactionSynchronizationManager.getResource(obtainEntityManagerFactory());
            EntityManager em = holder.getEntityManager();
            em.createNativeQuery("select set_config('app.academia_id', ?1, true)")
                    .setParameter(1, contexto.academiaId().toString())
                    .getSingleResult();
        });
    }
}
