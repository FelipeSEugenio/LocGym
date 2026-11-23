package com.locgym.pagamento.infraestrutura;

import com.locgym.compartilhado.paginacao.Pagina;
import com.locgym.compartilhado.tenancy.TenantContext;
import com.locgym.pagamento.dominio.Cobranca;
import com.locgym.pagamento.dominio.CobrancaRepositorio;
import com.locgym.pagamento.dominio.StatusCobranca;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class CobrancaRepositorioJpa implements CobrancaRepositorio {

    private static final Set<StatusCobranca> EM_ABERTO = Set.of(StatusCobranca.PENDENTE, StatusCobranca.ATRASADO);
    /** {@code not in ()} vazio não é SQL válido: usa um id que nunca existe. */
    private static final Set<UUID> NENHUMA = Set.of(new UUID(0, 0));

    private final CobrancaJpaDados dados;
    private final JdbcTemplate jdbc;

    CobrancaRepositorioJpa(CobrancaJpaDados dados, JdbcTemplate jdbc) {
        this.dados = dados;
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Cobranca> porId(UUID id) {
        return dados.findById(id).map(CobrancaJpa::paraDominio);
    }

    @Override
    public List<Cobranca> daMatricula(UUID matriculaId) {
        return dados.findByMatriculaIdOrderByPeriodoInicio(matriculaId).stream().map(CobrancaJpa::paraDominio).toList();
    }

    @Override
    public Optional<Cobranca> maisAntigaEmAberto(UUID matriculaId) {
        return emAbertoDaMatricula(matriculaId).stream().findFirst();
    }

    @Override
    public List<Cobranca> emAbertoDaMatricula(UUID matriculaId) {
        return dados.findByMatriculaIdAndStatusInOrderByPeriodoInicio(matriculaId, EM_ABERTO).stream()
                .map(CobrancaJpa::paraDominio).toList();
    }

    @Override
    public boolean temAtrasada(UUID matriculaId) {
        return dados.existsByMatriculaIdAndStatus(matriculaId, StatusCobranca.ATRASADO);
    }

    @Override
    public Optional<LocalDate> vencimentoAtrasadoMaisAntigo(UUID matriculaId) {
        return dados.menorVencimentoAtrasado(matriculaId);
    }

    @Override
    public Optional<LocalDate> maiorFimDePeriodo(UUID matriculaId) {
        return dados.maiorFimDePeriodo(matriculaId);
    }

    /**
     * {@code on conflict do nothing} torna a geração segura contra execuções concorrentes: a segunda espera a
     * primeira e não insere. RLS garante que {@code academia_id} é o da academia do contexto.
     */
    @Override
    public boolean inserirSeAusente(Cobranca c) {
        dados.flush();
        int linhas = jdbc.update("""
                insert into pagamento (id, academia_id, matricula_id, aluno_id, valor, vencimento, periodo_inicio,
                                       periodo_fim, status)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict on constraint ux_pagamento_matricula_periodo do nothing
                """, c.id(), TenantContext.exigir().academiaId(), c.matriculaId(), c.alunoId(), c.valor(),
                c.vencimento(), c.periodoInicio(), c.periodoFim(), c.status().name());
        return linhas == 1;
    }

    @Override
    public List<Cobranca> pendentesVencidas(LocalDate hoje, Set<UUID> matriculasIgnoradas) {
        return dados.pendentesVencidas(hoje, matriculasIgnoradas.isEmpty() ? NENHUMA : matriculasIgnoradas).stream()
                .map(CobrancaJpa::paraDominio).toList();
    }

    /** Flush imediato: conflito de versão vira erro aqui, dentro da transação, e consultas nativas veem a mudança. */
    @Override
    public void salvar(Cobranca cobranca) {
        CobrancaJpa entidade = dados.findById(cobranca.id())
                .orElseThrow(() -> new IllegalStateException("Cobrança inexistente: " + cobranca.id()));
        entidade.copiar(cobranca);
        dados.saveAndFlush(entidade);
    }

    @Override
    public Pagina<Cobranca> listar(FiltroCobrancas f, int pagina, int tamanho) {
        Specification<CobrancaJpa> spec = (raiz, consulta, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.status() != null) p.add(cb.equal(raiz.get("status"), f.status()));
            if (f.vencimentoDe() != null) p.add(cb.greaterThanOrEqualTo(raiz.get("vencimento"), f.vencimentoDe()));
            if (f.vencimentoAte() != null) p.add(cb.lessThanOrEqualTo(raiz.get("vencimento"), f.vencimentoAte()));
            if (f.alunoId() != null) p.add(cb.equal(raiz.get("alunoId"), f.alunoId()));
            return cb.and(p.toArray(Predicate[]::new));
        };
        var page = dados.findAll(spec, PageRequest.of(pagina, tamanho, Sort.by("vencimento", "id")));
        return new Pagina<>(page.map(CobrancaJpa::paraDominio).getContent(), pagina, tamanho, page.getTotalElements());
    }
}
