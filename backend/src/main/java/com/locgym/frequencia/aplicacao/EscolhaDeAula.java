package com.locgym.frequencia.aplicacao;

import com.locgym.agenda.AulaAberta;
import com.locgym.compartilhado.erros.RegraVioladaException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Escolha da aula de um check-in (opção B do design): a recepção escolhe quando há mais de uma aberta. */
public final class EscolhaDeAula {

    private EscolhaDeAula() {}

    /** Vazio = check-in livre (nenhuma aula aberta e nenhuma escolhida). */
    public static Optional<AulaAberta> escolher(List<AulaAberta> abertas, UUID horarioId) {
        if (horarioId != null) {
            return Optional.of(abertas.stream().filter(a -> a.horarioId().equals(horarioId)).findFirst()
                    .orElseThrow(() -> new RegraVioladaException("AULA_NAO_ABERTA",
                            "Esta aula não está aberta neste momento")));
        }
        if (abertas.size() > 1) {
            throw new RegraVioladaException("AULA_AMBIGUA", "Há mais de uma aula aberta: escolha a aula",
                    Map.of("aulas", abertas.stream().map(EscolhaDeAula::resumo).toList()));
        }
        return abertas.stream().findFirst();
    }

    private static Map<String, Object> resumo(AulaAberta a) {
        var m = new LinkedHashMap<String, Object>();
        m.put("horarioId", a.horarioId());
        m.put("tipo", a.tipo());
        m.put("professorNome", a.professorNome());
        m.put("inicio", a.inicio().toString());
        m.put("fim", a.fim().toString());
        return m;
    }
}
