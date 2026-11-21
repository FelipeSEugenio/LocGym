package com.locgym.compartilhado.eventos;

import java.util.UUID;

/** Evento usado apenas nos testes do outbox. */
record EventoTeste(UUID academiaId, String mensagem) implements EventoDominio {

    static final String TIPO = "teste.ocorrido";

    @Override
    public String tipo() {
        return TIPO;
    }

    @Override
    public int versao() {
        return 1;
    }
}
