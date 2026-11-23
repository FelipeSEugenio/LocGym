package com.locgym.matricula.web;

import com.locgym.compartilhado.paginacao.Pagina;
import com.locgym.matricula.aplicacao.ServicoMatriculas;
import com.locgym.matricula.aplicacao.ServicoMatriculas.Detalhe;
import com.locgym.matricula.dominio.Matricula;
import com.locgym.matricula.dominio.MatriculaListada;
import com.locgym.matricula.dominio.Periodo;
import com.locgym.matricula.dominio.StatusMatricula;
import com.locgym.pagamento.CobrancaResumo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MatriculasController {

    record NovaMatriculaRequisicao(@NotNull UUID alunoId, @NotNull UUID planoId, @NotNull LocalDate dataInicio,
                                   BigDecimal valor) {}

    record MotivoRequisicao(String motivo) {}

    record AbonoRequisicao(UUID ateCobrancaId, String motivo) {}

    record Referencia(UUID id, String nome) {}

    record MatriculaDetalhe(UUID id, Referencia aluno, Referencia plano, BigDecimal valor, String periodicidade,
                            LocalDate dataInicio, LocalDate vigenteAte, StatusMatricula status, LocalDate suspensaDesde,
                            String motivo, boolean inadimplente, List<Periodo> periodos, List<CobrancaResumo> cobrancas) {
        static MatriculaDetalhe de(Detalhe d) {
            Matricula m = d.matricula();
            return new MatriculaDetalhe(m.id(), new Referencia(d.aluno().id(), d.aluno().nome()),
                    new Referencia(d.plano().id(), d.plano().nome()), m.valor(), m.periodicidade(), m.dataInicio(),
                    m.vigenteAte(), m.status(), m.suspensaDesde(), m.motivo(), d.inadimplente(), d.periodos(),
                    d.cobrancas());
        }
    }

    private final ServicoMatriculas servico;

    MatriculasController(ServicoMatriculas servico) {
        this.servico = servico;
    }

    @GetMapping("/api/v1/matriculas")
    Pagina<MatriculaListada> listar(@RequestParam(required = false) StatusMatricula status,
                                    @RequestParam(required = false) String busca,
                                    @RequestParam(required = false) Integer pagina,
                                    @RequestParam(required = false) Integer tamanho) {
        return servico.listar(status, busca, Pagina.paginaValida(pagina), Pagina.tamanhoValido(tamanho));
    }

    @PostMapping("/api/v1/matriculas")
    @ResponseStatus(HttpStatus.CREATED)
    MatriculaDetalhe criar(@Valid @RequestBody NovaMatriculaRequisicao r) {
        return MatriculaDetalhe.de(servico.criar(r.alunoId(), r.planoId(), r.dataInicio(), r.valor()));
    }

    @GetMapping("/api/v1/matriculas/{id}")
    MatriculaDetalhe detalhe(@PathVariable UUID id) {
        return MatriculaDetalhe.de(servico.detalhe(id));
    }

    @GetMapping("/api/v1/alunos/{alunoId}/matricula")
    MatriculaDetalhe daAluno(@PathVariable UUID alunoId) {
        return MatriculaDetalhe.de(servico.daAluno(alunoId));
    }

    @PostMapping("/api/v1/matriculas/{id}/renovar")
    MatriculaDetalhe renovar(@PathVariable UUID id) {
        return MatriculaDetalhe.de(servico.renovar(id));
    }

    @PostMapping("/api/v1/matriculas/{id}/suspender")
    MatriculaDetalhe suspender(@PathVariable UUID id, @RequestBody MotivoRequisicao r) {
        return MatriculaDetalhe.de(servico.suspender(id, r.motivo()));
    }

    @PostMapping("/api/v1/matriculas/{id}/reativar")
    MatriculaDetalhe reativar(@PathVariable UUID id) {
        return MatriculaDetalhe.de(servico.reativar(id));
    }

    @PostMapping("/api/v1/matriculas/{id}/cancelar")
    MatriculaDetalhe cancelar(@PathVariable UUID id, @RequestBody MotivoRequisicao r) {
        return MatriculaDetalhe.de(servico.cancelar(id, r.motivo()));
    }

    @PostMapping("/api/v1/matriculas/{id}/abonar")
    MatriculaDetalhe abonar(@PathVariable UUID id, @RequestBody AbonoRequisicao r) {
        return MatriculaDetalhe.de(servico.abonar(id, r.ateCobrancaId(), r.motivo()));
    }
}
