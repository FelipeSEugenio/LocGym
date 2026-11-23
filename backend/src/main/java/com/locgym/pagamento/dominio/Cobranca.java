package com.locgym.pagamento.dominio;

import com.locgym.compartilhado.erros.RegraVioladaException;
import com.locgym.compartilhado.erros.ValorInvalidoException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Cobrança de um período da matrícula (tabela {@code pagamento}). Período {@code [periodoInicio, periodoFim)};
 * vence no primeiro dia do período. Pagamento sempre do valor integral.
 */
public class Cobranca {

    public static final String MOTIVO_MATRICULA_CANCELADA = "Matrícula cancelada";

    private final UUID id;
    private final UUID matriculaId;
    private final UUID alunoId;
    private final BigDecimal valor;
    private LocalDate vencimento;
    private LocalDate periodoInicio;
    private LocalDate periodoFim;
    private StatusCobranca status;
    private LocalDate dataPagamento;
    private FormaPagamento forma;
    private String observacoes;
    private String motivoCancelamento;

    public Cobranca(UUID id, UUID matriculaId, UUID alunoId, BigDecimal valor, LocalDate vencimento,
                    LocalDate periodoInicio, LocalDate periodoFim, StatusCobranca status, LocalDate dataPagamento,
                    FormaPagamento forma, String observacoes, String motivoCancelamento) {
        this.id = Objects.requireNonNull(id);
        this.matriculaId = Objects.requireNonNull(matriculaId);
        this.alunoId = Objects.requireNonNull(alunoId);
        this.valor = Objects.requireNonNull(valor);
        this.vencimento = Objects.requireNonNull(vencimento);
        this.periodoInicio = Objects.requireNonNull(periodoInicio);
        this.periodoFim = Objects.requireNonNull(periodoFim);
        this.status = Objects.requireNonNull(status);
        this.dataPagamento = dataPagamento;
        this.forma = forma;
        this.observacoes = observacoes;
        this.motivoCancelamento = motivoCancelamento;
    }

    /** Nasce ATRASADO quando o vencimento já passou (ex.: recuperação de períodos parados). */
    public static Cobranca nova(UUID matriculaId, UUID alunoId, BigDecimal valor, LocalDate periodoInicio,
                                LocalDate periodoFim, LocalDate hoje) {
        if (!periodoFim.isAfter(periodoInicio)) {
            throw new IllegalArgumentException("Período vazio: " + periodoInicio + " a " + periodoFim);
        }
        StatusCobranca status = periodoInicio.isBefore(hoje) ? StatusCobranca.ATRASADO : StatusCobranca.PENDENTE;
        return new Cobranca(UUID.randomUUID(), matriculaId, alunoId, valor, periodoInicio, periodoInicio, periodoFim,
                status, null, null, null, null);
    }

    public void registrarPagamento(LocalDate data, FormaPagamento forma, String observacoes, LocalDate hoje) {
        exigirEmAberto();
        if (data == null || data.isAfter(hoje)) {
            throw new ValorInvalidoException("dataPagamento", "A data do pagamento não pode ser futura");
        }
        if (forma == null) {
            throw new ValorInvalidoException("forma", "Informe a forma de pagamento");
        }
        if (observacoes != null && observacoes.strip().length() > 500) {
            throw new ValorInvalidoException("observacoes", "Use até 500 caracteres");
        }
        this.status = StatusCobranca.PAGO;
        this.dataPagamento = data;
        this.forma = forma;
        this.observacoes = observacoes == null || observacoes.isBlank() ? null : observacoes.strip();
    }

    /** Abono: o período é concedido sem pagamento (estende a vigência, não conta como recebido). */
    public void abonar(String motivo) {
        exigirEmAberto();
        this.motivoCancelamento = motivoValido(motivo);
        this.status = StatusCobranca.CANCELADO;
    }

    /** Cancelamento da matrícula: só a PENDENTE é cancelada; a ATRASADA continua devida. */
    public boolean cancelarPorMatricula() {
        if (status != StatusCobranca.PENDENTE) {
            return false;
        }
        this.status = StatusCobranca.CANCELADO;
        this.motivoCancelamento = MOTIVO_MATRICULA_CANCELADA;
        return true;
    }

    /** @return {@code true} se passou de PENDENTE para ATRASADO (vence no próprio dia do vencimento só a partir do dia seguinte). */
    public boolean marcarAtrasadaSeVencida(LocalDate hoje) {
        if (status != StatusCobranca.PENDENTE || !vencimento.isBefore(hoje)) {
            return false;
        }
        status = StatusCobranca.ATRASADO;
        return true;
    }

    /** Reativação de matrícula suspensa: empurra a cobrança pendente pelos dias suspensos. */
    public void deslocar(int dias) {
        if (status != StatusCobranca.PENDENTE) {
            throw new IllegalStateException("Só cobrança PENDENTE é deslocada; status " + status);
        }
        vencimento = vencimento.plusDays(dias);
        periodoInicio = periodoInicio.plusDays(dias);
        periodoFim = periodoFim.plusDays(dias);
    }

    private void exigirEmAberto() {
        if (!status.emAberto()) {
            throw new RegraVioladaException("COBRANCA_NAO_EM_ABERTO", "Esta cobrança já foi paga ou cancelada");
        }
    }

    static String motivoValido(String motivo) {
        if (motivo == null || motivo.isBlank() || motivo.strip().length() > 300) {
            throw new ValorInvalidoException("motivo", "Informe um motivo de até 300 caracteres");
        }
        return motivo.strip();
    }

    public UUID id() { return id; }
    public UUID matriculaId() { return matriculaId; }
    public UUID alunoId() { return alunoId; }
    public BigDecimal valor() { return valor; }
    public LocalDate vencimento() { return vencimento; }
    public LocalDate periodoInicio() { return periodoInicio; }
    public LocalDate periodoFim() { return periodoFim; }
    public StatusCobranca status() { return status; }
    public LocalDate dataPagamento() { return dataPagamento; }
    public FormaPagamento forma() { return forma; }
    public String observacoes() { return observacoes; }
    public String motivoCancelamento() { return motivoCancelamento; }
}
