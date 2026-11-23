package com.locgym.matricula.dominio;

import com.locgym.compartilhado.erros.RegraVioladaException;
import com.locgym.compartilhado.erros.ValorInvalidoException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Matrícula de um aluno num plano. {@code vigenteAte} é o primeiro dia não coberto (= vencimento exibido);
 * {@code dataBase} é a âncora do calendário de períodos e desloca junto com as suspensões.
 */
public class Matricula {

    /** Janela permitida para a data de início, em dias antes ou depois de hoje. */
    public static final int JANELA_INICIO_DIAS = 30;

    public record QuitacaoAplicada(boolean primeira, boolean ativou) {}

    private final UUID id;
    private final UUID alunoId;
    private final UUID planoId;
    private final BigDecimal valor;
    private final String periodicidade;
    private final LocalDate dataInicio;
    private LocalDate dataBase;
    private LocalDate vigenteAte;
    private StatusMatricula status;
    private LocalDate suspensaDesde;
    private String motivo;

    public Matricula(UUID id, UUID alunoId, UUID planoId, BigDecimal valor, String periodicidade, LocalDate dataInicio,
                     LocalDate dataBase, LocalDate vigenteAte, StatusMatricula status, LocalDate suspensaDesde,
                     String motivo) {
        this.id = Objects.requireNonNull(id);
        this.alunoId = Objects.requireNonNull(alunoId);
        this.planoId = Objects.requireNonNull(planoId);
        this.valor = Objects.requireNonNull(valor);
        this.periodicidade = Objects.requireNonNull(periodicidade);
        CalendarioDePeriodos.meses(periodicidade);
        this.dataInicio = Objects.requireNonNull(dataInicio);
        this.dataBase = Objects.requireNonNull(dataBase);
        this.vigenteAte = Objects.requireNonNull(vigenteAte);
        this.status = Objects.requireNonNull(status);
        this.suspensaDesde = suspensaDesde;
        this.motivo = motivo;
    }

    public static Matricula nova(UUID alunoId, UUID planoId, BigDecimal valor, String periodicidade,
                                 LocalDate dataInicio, LocalDate hoje) {
        if (dataInicio == null || Math.abs(ChronoUnit.DAYS.between(hoje, dataInicio)) > JANELA_INICIO_DIAS) {
            throw new ValorInvalidoException("dataInicio", "A data de início deve estar entre 30 dias antes e 30 dias depois de hoje");
        }
        if (valor == null || valor.signum() <= 0 || valor.stripTrailingZeros().scale() > 2) {
            throw new ValorInvalidoException("valor", "O valor deve ser maior que zero, com até 2 casas decimais");
        }
        return new Matricula(UUID.randomUUID(), alunoId, planoId, valor.setScale(2), periodicidade, dataInicio,
                dataInicio, dataInicio, StatusMatricula.PENDENTE, null, null);
    }

    private int meses() {
        return CalendarioDePeriodos.meses(periodicidade);
    }

    public Periodo primeiroPeriodo() {
        return CalendarioDePeriodos.periodoQueComecaEm(dataBase, meses(), dataBase);
    }

    public Periodo proximoPeriodo() {
        return CalendarioDePeriodos.periodoQueComecaEm(dataBase, meses(), vigenteAte);
    }

    /**
     * Pagamento ou abono da cobrança do período seguinte à vigência. Volta a ATIVA (de PENDENTE ou VENCIDA) só
     * quando não resta cobrança atrasada e a vigência cobre hoje.
     */
    public QuitacaoAplicada aplicarQuitacao(Periodo periodo, boolean restamAtrasadas, LocalDate hoje) {
        if (status == StatusMatricula.CANCELADA) {
            throw new IllegalStateException("Matrícula cancelada não recebe quitação");
        }
        if (!periodo.inicio().equals(vigenteAte)) {
            throw new IllegalStateException("Quitação fora de sequência: período " + periodo.inicio()
                    + ", vigência até " + vigenteAte);
        }
        boolean primeira = status == StatusMatricula.PENDENTE && vigenteAte.equals(dataBase);
        vigenteAte = periodo.fim();
        boolean ativou = false;
        if ((status == StatusMatricula.PENDENTE || status == StatusMatricula.VENCIDA)
                && !restamAtrasadas && vigenteAte.isAfter(hoje)) {
            status = StatusMatricula.ATIVA;
            ativou = true;
        }
        return new QuitacaoAplicada(primeira, ativou);
    }

    /**
     * Períodos que já deveriam ter cobrança: a partir de {@code inicioSemCobranca} (primeiro período ainda sem
     * cobrança), todos cujo início menos a antecedência já chegou — inclusive meses parados.
     */
    public List<Periodo> periodosAGerar(LocalDate inicioSemCobranca, int diasAntecedencia, LocalDate hoje) {
        List<Periodo> periodos = new ArrayList<>();
        LocalDate inicio = inicioSemCobranca;
        while (!inicio.minusDays(diasAntecedencia).isAfter(hoje)) {
            Periodo p = CalendarioDePeriodos.periodoQueComecaEm(dataBase, meses(), inicio);
            periodos.add(p);
            inicio = p.fim();
        }
        return periodos;
    }

    public void suspender(String motivo, LocalDate hoje) {
        if (status != StatusMatricula.ATIVA) {
            throw new RegraVioladaException("MATRICULA_NAO_ATIVA", "Só matrícula ativa pode ser suspensa");
        }
        this.motivo = motivoValido(motivo);
        this.suspensaDesde = hoje;
        this.status = StatusMatricula.SUSPENSA;
    }

    /** @return dias suspensos — a vigência e a âncora andam esse tanto. */
    public int reativar(LocalDate hoje) {
        if (status != StatusMatricula.SUSPENSA) {
            throw new RegraVioladaException("MATRICULA_NAO_SUSPENSA", "Só matrícula suspensa pode ser reativada");
        }
        int dias = (int) Math.max(0, ChronoUnit.DAYS.between(suspensaDesde, hoje));
        dataBase = dataBase.plusDays(dias);
        vigenteAte = vigenteAte.plusDays(dias);
        suspensaDesde = null;
        status = StatusMatricula.ATIVA;
        return dias;
    }

    public void cancelar(String motivo) {
        if (status == StatusMatricula.CANCELADA) {
            throw new RegraVioladaException("MATRICULA_JA_CANCELADA", "A matrícula já está cancelada");
        }
        this.motivo = motivoValido(motivo);
        this.suspensaDesde = null;
        this.status = StatusMatricula.CANCELADA;
    }

    /** @return {@code true} se uma matrícula ATIVA passou a VENCIDA. */
    public boolean vencerSe(boolean temAtrasadaAlemDaCarencia) {
        if (status != StatusMatricula.ATIVA || !temAtrasadaAlemDaCarencia) {
            return false;
        }
        status = StatusMatricula.VENCIDA;
        return true;
    }

    private static String motivoValido(String motivo) {
        if (motivo == null || motivo.isBlank() || motivo.strip().length() > 300) {
            throw new ValorInvalidoException("motivo", "Informe um motivo de até 300 caracteres");
        }
        return motivo.strip();
    }

    public UUID id() { return id; }
    public UUID alunoId() { return alunoId; }
    public UUID planoId() { return planoId; }
    public BigDecimal valor() { return valor; }
    public String periodicidade() { return periodicidade; }
    public LocalDate dataInicio() { return dataInicio; }
    public LocalDate dataBase() { return dataBase; }
    public LocalDate vigenteAte() { return vigenteAte; }
    public StatusMatricula status() { return status; }
    public LocalDate suspensaDesde() { return suspensaDesde; }
    public String motivo() { return motivo; }
}
