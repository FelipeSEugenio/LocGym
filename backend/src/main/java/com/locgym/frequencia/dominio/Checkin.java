package com.locgym.frequencia.dominio;

import com.locgym.compartilhado.erros.RegraVioladaException;
import com.locgym.compartilhado.erros.ValorInvalidoException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * Entrada de um aluno: numa aula (com pontualidade) ou livre (sem aula aberta, sem pontualidade).
 * ATIVO pode ser EXCLUIDO ou CORRIGIDO (substituído por um novo registro); esses dois são finais.
 */
public class Checkin {

    public static final int DIAS_RETROATIVOS = 30;

    private final UUID id;
    private final UUID alunoId;
    private final UUID aulaId;
    private final LocalDate data;
    private final LocalTime hora;
    private final Instant registradoEm;
    private final Origem origem;
    private final Pontualidade pontualidade;
    private final UUID registradoPor;
    private StatusCheckin status;
    private String motivo;
    private UUID substituidoPor;

    public Checkin(UUID id, UUID alunoId, UUID aulaId, LocalDate data, LocalTime hora, Instant registradoEm,
                   Origem origem, Pontualidade pontualidade, StatusCheckin status, String motivo,
                   UUID substituidoPor, UUID registradoPor) {
        this.id = Objects.requireNonNull(id);
        this.alunoId = Objects.requireNonNull(alunoId);
        this.aulaId = aulaId;
        this.data = Objects.requireNonNull(data);
        this.hora = Objects.requireNonNull(hora);
        this.registradoEm = Objects.requireNonNull(registradoEm);
        this.origem = Objects.requireNonNull(origem);
        this.pontualidade = pontualidade;
        this.status = Objects.requireNonNull(status);
        this.motivo = motivo;
        this.substituidoPor = substituidoPor;
        this.registradoPor = registradoPor;
    }

    /** {@code aulaId} e {@code inicioAula} nulos = check-in livre. A hora é truncada para minutos. */
    public static Checkin registrar(UUID alunoId, UUID aulaId, LocalTime inicioAula, LocalDate data, LocalTime hora,
                                    Origem origem, int tolerancia, UUID registradoPor, Instant registradoEm) {
        LocalTime minuto = hora.truncatedTo(ChronoUnit.MINUTES);
        Pontualidade p = aulaId == null ? null : Pontualidade.de(inicioAula, minuto, tolerancia);
        return new Checkin(UUID.randomUUID(), alunoId, aulaId, data, minuto, registradoEm, origem, p,
                StatusCheckin.ATIVO, null, null, registradoPor);
    }

    /** Presença manual e correção: no máximo 30 dias para trás e nunca no futuro. */
    public static void validarMomento(LocalDate data, LocalTime hora, LocalDateTime agora) {
        if (data.isBefore(agora.toLocalDate().minusDays(DIAS_RETROATIVOS))) {
            throw new ValorInvalidoException("data", "Informe uma data dos últimos 30 dias");
        }
        if (data.atTime(hora.truncatedTo(ChronoUnit.MINUTES)).isAfter(agora)) {
            throw new ValorInvalidoException("hora", "A data e a hora não podem estar no futuro");
        }
    }

    public void excluir(String motivo) {
        String m = validarMotivo(motivo);
        exigirAtivo();
        this.status = StatusCheckin.EXCLUIDO;
        this.motivo = m;
    }

    /** Marca este registro como CORRIGIDO e devolve o novo, ATIVO, com a mesma origem e pontualidade recalculada. */
    public Checkin corrigir(UUID novaAulaId, LocalTime novoInicioAula, LocalDate novaData, LocalTime novaHora,
                            String motivo, int tolerancia, UUID usuario, Instant agora) {
        String m = validarMotivo(motivo);
        exigirAtivo();
        Checkin novo = registrar(alunoId, novaAulaId, novoInicioAula, novaData, novaHora, origem, tolerancia, usuario,
                agora);
        this.status = StatusCheckin.CORRIGIDO;
        this.motivo = m;
        this.substituidoPor = novo.id;
        return novo;
    }

    private void exigirAtivo() {
        if (status != StatusCheckin.ATIVO) {
            throw new RegraVioladaException("CHECKIN_NAO_ATIVO", "Este registro já foi corrigido ou excluído");
        }
    }

    private static String validarMotivo(String motivo) {
        if (motivo == null || motivo.isBlank() || motivo.strip().length() > 300) {
            throw new ValorInvalidoException("motivo", "Informe o motivo com até 300 caracteres");
        }
        return motivo.strip();
    }

    public UUID id() { return id; }
    public UUID alunoId() { return alunoId; }
    public UUID aulaId() { return aulaId; }
    public boolean livre() { return aulaId == null; }
    public LocalDate data() { return data; }
    public LocalTime hora() { return hora; }
    public Instant registradoEm() { return registradoEm; }
    public Origem origem() { return origem; }
    public Pontualidade pontualidade() { return pontualidade; }
    public StatusCheckin status() { return status; }
    public String motivo() { return motivo; }
    public UUID substituidoPor() { return substituidoPor; }
    public UUID registradoPor() { return registradoPor; }
}
