package com.locgym.pagamento.infraestrutura;

import com.locgym.pagamento.dominio.StatusCobranca;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/** Consultas filtradas automaticamente pela academia ativa (@TenantId) e protegidas por RLS. */
interface CobrancaJpaDados extends JpaRepository<CobrancaJpa, UUID>, JpaSpecificationExecutor<CobrancaJpa> {

    List<CobrancaJpa> findByMatriculaIdOrderByPeriodoInicio(UUID matriculaId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CobrancaJpa c where c.id = :id")
    Optional<CobrancaJpa> buscarParaAtualizar(UUID id);

    List<CobrancaJpa> findByMatriculaIdAndStatusInOrderByPeriodoInicio(UUID matriculaId, Collection<StatusCobranca> status);

    boolean existsByMatriculaIdAndStatus(UUID matriculaId, StatusCobranca status);

    boolean existsByAlunoIdAndStatus(UUID alunoId, StatusCobranca status);

    @Query("select min(c.vencimento) from CobrancaJpa c where c.matriculaId = :matriculaId and c.status = 'ATRASADO'")
    Optional<LocalDate> menorVencimentoAtrasado(UUID matriculaId);

    @Query("select max(c.periodoFim) from CobrancaJpa c where c.matriculaId = :matriculaId")
    Optional<LocalDate> maiorFimDePeriodo(UUID matriculaId);

    @Query("""
            select c from CobrancaJpa c
             where c.status = 'PENDENTE' and c.vencimento < :hoje
               and c.matriculaId not in :ignoradas
             order by c.vencimento, c.id
            """)
    List<CobrancaJpa> pendentesVencidas(LocalDate hoje, Collection<UUID> ignoradas);
}
