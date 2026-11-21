package com.locgym.compartilhado.eventos.infraestrutura;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_evento")
class OutboxEvento {

    @Id
    private UUID id;

    /** Ordem de gravação, atribuída pelo banco (identity). */
    @Column(insertable = false, updatable = false)
    private Long sequencia;

    private String tipo;
    private UUID academiaId;
    private int versao;

    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    private Instant ocorridoEm;
    private Instant publicadoEm;
    private int tentativas;
    private String ultimoErro;

    protected OutboxEvento() {}

    OutboxEvento(UUID id, String tipo, UUID academiaId, int versao, String payload, Instant ocorridoEm) {
        this.id = id;
        this.tipo = tipo;
        this.academiaId = academiaId;
        this.versao = versao;
        this.payload = payload;
        this.ocorridoEm = ocorridoEm;
    }

    /** Chave da mensagem Kafka: a academia mantém seus eventos na mesma partição (ordem por agregado). */
    String chave() {
        return academiaId != null ? academiaId.toString() : id.toString();
    }

    void marcarPublicado(Instant quando) {
        this.publicadoEm = quando;
        this.ultimoErro = null;
    }

    void registrarFalha(String erro) {
        this.tentativas++;
        this.ultimoErro = erro;
    }

    UUID id() { return id; }
    String tipo() { return tipo; }
    String payload() { return payload; }
    int tentativas() { return tentativas; }
}
