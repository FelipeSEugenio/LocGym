package com.locgym.compartilhado.tenancy;

import java.util.Optional;

/** Contexto de tenant da thread atual. Definido pelo filtro de requisição e sempre limpo ao final dela. */
public final class TenantContext {

    private static final ThreadLocal<ContextoTenant> ATUAL = new ThreadLocal<>();

    private TenantContext() {}

    public static Optional<ContextoTenant> atual() {
        return Optional.ofNullable(ATUAL.get());
    }

    /** @throws IllegalStateException se não houver academia ativa (rota de tenant chamada sem contexto). */
    public static ContextoTenant exigir() {
        return atual().orElseThrow(() -> new IllegalStateException("Nenhuma academia ativa no contexto"));
    }

    public static void definir(ContextoTenant contexto) {
        ATUAL.set(contexto);
    }

    public static void limpar() {
        ATUAL.remove();
    }
}
