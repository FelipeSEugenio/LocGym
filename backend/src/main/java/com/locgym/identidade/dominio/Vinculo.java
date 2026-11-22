package com.locgym.identidade.dominio;

import com.locgym.compartilhado.tenancy.Perfil;
import java.util.UUID;

/** Vínculo de um usuário com uma academia, num perfil. Aluno CANCELADO não gera vínculo. */
public record Vinculo(UUID academiaId, String academiaNome, String academiaStatus, Perfil perfil) {

    public boolean academiaAtiva() {
        return "ATIVA".equals(academiaStatus);
    }
}
