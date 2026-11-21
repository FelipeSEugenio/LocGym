package com.locgym.compartilhado.eventos;

import java.util.UUID;

/** Evento de teste com tipo (tópico) arbitrário — força o produtor a buscar metadados de um tópico novo. */
record EventoDinamico(String tipo, UUID academiaId) implements EventoDominio {

    @Override
    public int versao() {
        return 1;
    }
}
