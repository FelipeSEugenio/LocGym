package com.locgym.pagamento.web;

import com.locgym.aluno.Alunos;
import com.locgym.compartilhado.paginacao.Pagina;
import com.locgym.pagamento.CobrancaResumo;
import com.locgym.pagamento.aplicacao.ServicoCobrancas;
import com.locgym.pagamento.dominio.Cobranca;
import com.locgym.pagamento.dominio.CobrancaRepositorio.FiltroCobrancas;
import com.locgym.pagamento.dominio.FormaPagamento;
import com.locgym.pagamento.dominio.StatusCobranca;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cobrancas")
class CobrancasController {

    record PagamentoRequisicao(@NotNull LocalDate dataPagamento, @NotNull FormaPagamento forma,
                               @Size(max = 500) String observacoes) {}

    record MotivoRequisicao(String motivo) {}

    record CobrancaResposta(UUID id, UUID matriculaId, UUID alunoId, String alunoNome, BigDecimal valor,
                            LocalDate vencimento, LocalDate periodoInicio, LocalDate periodoFim, String status,
                            LocalDate dataPagamento, String forma, String observacoes, String motivoCancelamento) {
        static CobrancaResposta de(CobrancaResumo c, String alunoNome) {
            return new CobrancaResposta(c.id(), c.matriculaId(), c.alunoId(), alunoNome, c.valor(), c.vencimento(),
                    c.periodoInicio(), c.periodoFim(), c.status(), c.dataPagamento(), c.forma(), c.observacoes(),
                    c.motivoCancelamento());
        }
    }

    private final ServicoCobrancas servico;
    private final Alunos alunos;

    CobrancasController(ServicoCobrancas servico, Alunos alunos) {
        this.servico = servico;
        this.alunos = alunos;
    }

    @GetMapping
    Pagina<CobrancaResposta> listar(@RequestParam(required = false) StatusCobranca status,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vencimentoDe,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vencimentoAte,
                                    @RequestParam(required = false) UUID alunoId,
                                    @RequestParam(required = false) Integer pagina,
                                    @RequestParam(required = false) Integer tamanho) {
        Pagina<Cobranca> pag = servico.listar(new FiltroCobrancas(status, vencimentoDe, vencimentoAte, alunoId),
                Pagina.paginaValida(pagina), Pagina.tamanhoValido(tamanho));
        var nomes = alunos.nomes(pag.itens().stream().map(Cobranca::alunoId).distinct().toList());
        return pag.mapear(c -> CobrancaResposta.de(ServicoCobrancas.resumo(c), nomes.get(c.alunoId())));
    }

    @PostMapping("/{id}/pagamento")
    CobrancaResposta registrarPagamento(@PathVariable UUID id, @Valid @RequestBody PagamentoRequisicao r) {
        return comNome(servico.registrarPagamento(id, r.dataPagamento(), r.forma(), r.observacoes()));
    }

    @PostMapping("/{id}/cancelar")
    CobrancaResposta abonar(@PathVariable UUID id, @RequestBody MotivoRequisicao r) {
        return comNome(servico.abonar(id, r.motivo()));
    }

    private CobrancaResposta comNome(Cobranca c) {
        return CobrancaResposta.de(ServicoCobrancas.resumo(c), alunos.nomes(java.util.List.of(c.alunoId())).get(c.alunoId()));
    }
}
