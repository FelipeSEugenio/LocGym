package com.locgym.identidade.infraestrutura;

import com.locgym.identidade.dominio.RefreshToken;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "refresh_token")
class RefreshTokenJpa {

    @Id
    private UUID id;
    private UUID usuarioId;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 64)
    private String tokenHash;
    private UUID familia;
    private Instant expiraEm;
    private Instant usadoEm;
    private Instant revogadoEm;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant criadoEm;

    protected RefreshTokenJpa() {}

    static RefreshTokenJpa de(RefreshToken t) {
        RefreshTokenJpa e = new RefreshTokenJpa();
        e.id = t.id();
        e.usuarioId = t.usuarioId();
        e.tokenHash = t.tokenHash();
        e.familia = t.familia();
        e.expiraEm = t.expiraEm();
        e.copiar(t);
        return e;
    }

    void copiar(RefreshToken t) {
        this.usadoEm = t.usadoEm();
        this.revogadoEm = t.revogadoEm();
    }

    RefreshToken paraDominio() {
        return new RefreshToken(id, usuarioId, tokenHash, familia, expiraEm, usadoEm, revogadoEm);
    }
}
