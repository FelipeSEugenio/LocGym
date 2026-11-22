package com.locgym.compartilhado.tenancy;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Base das entidades de tenant: {@code academia_id} é preenchido e filtrado pelo Hibernate a partir do
 * contexto — nunca vem da requisição.
 */
@MappedSuperclass
public abstract class EntidadeDeTenant {

    @TenantId
    @Column(name = "academia_id", nullable = false, updatable = false)
    private UUID academiaId;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    private Instant atualizadoEm;

    public UUID academiaId() { return academiaId; }
    public Instant criadoEm() { return criadoEm; }
    public Instant atualizadoEm() { return atualizadoEm; }
}
