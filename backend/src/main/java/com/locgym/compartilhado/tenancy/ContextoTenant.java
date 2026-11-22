package com.locgym.compartilhado.tenancy;

import java.util.Objects;
import java.util.UUID;

/** Academia e perfil ativos na requisição, já validados contra os vínculos do usuário no banco. */
public record ContextoTenant(UUID academiaId, Perfil perfil, boolean academiaAtiva, UUID usuarioId) {

    public ContextoTenant {
        Objects.requireNonNull(academiaId);
        Objects.requireNonNull(perfil);
        Objects.requireNonNull(usuarioId);
    }
}
