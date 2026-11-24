package com.locgym.frequencia.aplicacao;

import static java.time.LocalTime.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.locgym.agenda.AulaAberta;
import com.locgym.compartilhado.erros.RegraVioladaException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EscolhaDeAulaTest {

    final AulaAberta gi = new AulaAberta(UUID.randomUUID(), null, "Gi", UUID.randomUUID(), "Ana", of(19, 0), of(20, 0));
    final AulaAberta kids = new AulaAberta(UUID.randomUUID(), null, "Kids", UUID.randomUUID(), "Bruno", of(19, 0), of(20, 0));

    @Test
    void nenhumaELivre() {
        assertThat(EscolhaDeAula.escolher(List.of(), null)).isEmpty();
    }

    @Test
    void umaEAutomatica() {
        assertThat(EscolhaDeAula.escolher(List.of(gi), null)).contains(gi);
        assertThat(EscolhaDeAula.escolher(List.of(gi), gi.horarioId())).contains(gi);
    }

    @Test
    void variasSemEscolhaAmbigua() {
        assertThatThrownBy(() -> EscolhaDeAula.escolher(List.of(gi, kids), null))
                .isInstanceOfSatisfying(RegraVioladaException.class, e -> {
                    assertThat(e.codigo()).isEqualTo("AULA_AMBIGUA");
                    assertThat((List<?>) e.extras().get("aulas")).hasSize(2);
                });
    }

    @Test
    void variasComEscolha() {
        assertThat(EscolhaDeAula.escolher(List.of(gi, kids), kids.horarioId())).contains(kids);
    }

    @Test
    void escolhaForaDasAbertas() {
        assertThatThrownBy(() -> EscolhaDeAula.escolher(List.of(gi), UUID.randomUUID()))
                .isInstanceOf(RegraVioladaException.class).extracting("codigo").isEqualTo("AULA_NAO_ABERTA");
        assertThatThrownBy(() -> EscolhaDeAula.escolher(List.of(), UUID.randomUUID()))
                .isInstanceOf(RegraVioladaException.class).extracting("codigo").isEqualTo("AULA_NAO_ABERTA");
    }
}
