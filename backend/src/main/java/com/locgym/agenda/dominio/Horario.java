package com.locgym.agenda.dominio;

import com.locgym.compartilhado.erros.ValorInvalidoException;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/** Aula fixa da grade semanal. {@code diaSemana} segue a ISO: 1 = segunda … 7 = domingo. Não cruza a meia-noite. */
public class Horario {

    private final UUID id;
    private UUID professorId;
    private int diaSemana;
    private LocalTime inicio;
    private LocalTime fim;
    private String tipo;
    private StatusHorario status;

    public Horario(UUID id, UUID professorId, int diaSemana, LocalTime inicio, LocalTime fim, String tipo,
                   StatusHorario status) {
        this.id = Objects.requireNonNull(id);
        this.status = Objects.requireNonNull(status);
        aplicar(professorId, diaSemana, inicio, fim, tipo);
    }

    public static Horario novo(UUID professorId, int diaSemana, LocalTime inicio, LocalTime fim, String tipo) {
        return new Horario(UUID.randomUUID(), professorId, diaSemana, inicio, fim, tipo, StatusHorario.ATIVO);
    }

    public void alterar(UUID professorId, int diaSemana, LocalTime inicio, LocalTime fim, String tipo,
                        StatusHorario status) {
        aplicar(professorId, diaSemana, inicio, fim, tipo);
        this.status = Objects.requireNonNull(status);
    }

    private void aplicar(UUID professorId, int diaSemana, LocalTime inicio, LocalTime fim, String tipo) {
        Objects.requireNonNull(professorId);
        if (diaSemana < 1 || diaSemana > 7) {
            throw new ValorInvalidoException("diaSemana", "Informe um dia da semana de 1 (segunda) a 7 (domingo)");
        }
        if (inicio == null) {
            throw new ValorInvalidoException("inicio", "Informe o início");
        }
        if (fim == null) {
            throw new ValorInvalidoException("fim", "Informe o fim");
        }
        LocalTime i = inicio.truncatedTo(ChronoUnit.MINUTES);
        LocalTime f = fim.truncatedTo(ChronoUnit.MINUTES);
        if (!f.isAfter(i)) {
            throw new ValorInvalidoException("fim", "O fim deve ser depois do início, no mesmo dia");
        }
        if (tipo == null || tipo.isBlank() || tipo.strip().length() > 60) {
            throw new ValorInvalidoException("tipo", "Informe o tipo de aula com até 60 caracteres");
        }
        this.professorId = professorId;
        this.diaSemana = diaSemana;
        this.inicio = i;
        this.fim = f;
        this.tipo = tipo.strip();
    }

    public UUID id() { return id; }
    public UUID professorId() { return professorId; }
    public int diaSemana() { return diaSemana; }
    public LocalTime inicio() { return inicio; }
    public LocalTime fim() { return fim; }
    public String tipo() { return tipo; }
    public StatusHorario status() { return status; }
    public boolean ativo() { return status == StatusHorario.ATIVO; }
}
