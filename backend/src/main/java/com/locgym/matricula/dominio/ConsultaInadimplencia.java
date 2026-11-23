package com.locgym.matricula.dominio;

import com.locgym.compartilhado.paginacao.Pagina;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Modelo de leitura: alunos com cobrança ATRASADA na academia ativa (inadimplência é derivada, não armazenada). */
public interface ConsultaInadimplencia {

    record Linha(UUID alunoId, String nome, String cpf, BigDecimal totalEmAtraso, int cobrancasAtrasadas,
                 LocalDate vencimentoMaisAntigo, UUID matriculaId, String statusMatricula) {}

    /** Ordenada pelo vencimento mais antigo (mais dias de atraso primeiro) e pelo nome. */
    Pagina<Linha> listar(String buscaOuNulo, int pagina, int tamanho);
}
