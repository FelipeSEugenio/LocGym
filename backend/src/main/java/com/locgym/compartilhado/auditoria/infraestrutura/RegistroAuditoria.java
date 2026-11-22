package com.locgym.compartilhado.auditoria.infraestrutura;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "auditoria")
class RegistroAuditoria {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "academia_id", nullable = false, updatable = false)
    private UUID academiaId;

    private UUID usuarioId;
    private String acao;
    private String entidade;
    private UUID entidadeId;

    @JdbcTypeCode(SqlTypes.JSON)
    private String antes;

    @JdbcTypeCode(SqlTypes.JSON)
    private String depois;

    private Instant ocorridoEm;

    protected RegistroAuditoria() {}

    RegistroAuditoria(UUID usuarioId, String acao, String entidade, UUID entidadeId, String antes, String depois,
                      Instant ocorridoEm) {
        this.id = UUID.randomUUID();
        this.usuarioId = usuarioId;
        this.acao = acao;
        this.entidade = entidade;
        this.entidadeId = entidadeId;
        this.antes = antes;
        this.depois = depois;
        this.ocorridoEm = ocorridoEm;
    }
}
