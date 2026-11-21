package com.locgym.compartilhado.eventos.infraestrutura;

import com.locgym.compartilhado.eventos.EventoDominio;
import com.locgym.compartilhado.eventos.PublicadorEventos;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Grava eventos no outbox na transação do chamador, já no formato de envelope publicado. */
@Component
class PublicadorOutbox implements PublicadorEventos {

    private final OutboxRepositorio repositorio;
    private final JsonMapper json;
    private final Clock clock;

    PublicadorOutbox(OutboxRepositorio repositorio, JsonMapper json, Clock clock) {
        this.repositorio = repositorio;
        this.json = json;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publicar(EventoDominio evento) {
        UUID eventoId = UUID.randomUUID();
        Instant ocorridoEm = clock.instant();

        ObjectNode dados = json.valueToTree(evento);
        dados.remove("tipo");
        dados.remove("versao");
        dados.remove("academiaId");

        ObjectNode envelope = json.createObjectNode();
        envelope.put("evento_id", eventoId.toString());
        envelope.put("tipo", evento.tipo());
        if (evento.academiaId() != null) {
            envelope.put("academia_id", evento.academiaId().toString());
        } else {
            envelope.putNull("academia_id");
        }
        envelope.put("ocorrido_em", ocorridoEm.toString());
        envelope.put("versao", evento.versao());
        envelope.set("dados", dados);

        repositorio.save(new OutboxEvento(
                eventoId, evento.tipo(), evento.academiaId(), evento.versao(), json.writeValueAsString(envelope), ocorridoEm));
    }
}
