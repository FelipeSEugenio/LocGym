package com.locgym.compartilhado.tenancy;

import java.util.UUID;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/**
 * Informa ao Hibernate a academia ativa: ele grava {@code academia_id} nos INSERTs e filtra as consultas
 * das entidades com {@code @TenantId}. Sem contexto, devolve um UUID nulo — nada visível, nenhum insert aceito.
 */
public class ResolvedorTenant implements CurrentTenantIdentifierResolver<UUID> {

    public static final UUID SEM_ACADEMIA = new UUID(0L, 0L);

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        return TenantContext.atual().map(ContextoTenant::academiaId).orElse(SEM_ACADEMIA);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }
}
