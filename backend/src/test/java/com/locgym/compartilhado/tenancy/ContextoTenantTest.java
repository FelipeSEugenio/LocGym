package com.locgym.compartilhado.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContextoTenantTest {

    @Test
    void usuarioObrigatorioForaDoSistema() {
        assertThatThrownBy(() -> new ContextoTenant(UUID.randomUUID(), Perfil.ADMIN, true, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void contextoDeSistemaSemUsuario() {
        UUID academia = UUID.randomUUID();
        ContextoTenant c = ContextoTenant.sistema(academia);

        assertThat(c.academiaId()).isEqualTo(academia);
        assertThat(c.perfil()).isEqualTo(Perfil.SISTEMA);
        assertThat(c.academiaAtiva()).isTrue();
        assertThat(c.usuarioId()).isNull();
    }
}
