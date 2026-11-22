package com.locgym.identidade.infraestrutura;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface RefreshTokenJpaDados extends JpaRepository<RefreshTokenJpa, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshTokenJpa> findByTokenHash(String tokenHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshTokenJpa t set t.revogadoEm = :agora where t.familia = :familia and t.revogadoEm is null")
    int revogarFamilia(UUID familia, Instant agora);
}
