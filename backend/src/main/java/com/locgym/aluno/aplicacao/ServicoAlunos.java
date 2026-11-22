package com.locgym.aluno.aplicacao;

import com.locgym.aluno.dominio.Aluno;
import com.locgym.aluno.dominio.AlunoRepositorio;
import com.locgym.aluno.dominio.StatusAluno;
import com.locgym.compartilhado.auditoria.AcaoAuditada;
import com.locgym.compartilhado.auditoria.Auditoria;
import com.locgym.compartilhado.erros.NaoEncontradoException;
import com.locgym.compartilhado.erros.RegraVioladaException;
import com.locgym.compartilhado.paginacao.Pagina;
import com.locgym.compartilhado.tenancy.TenantContext;
import com.locgym.identidade.ContasDeUsuario;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicoAlunos {

    private final AlunoRepositorio alunos;
    private final ContasDeUsuario contas;
    private final Auditoria auditoria;

    public ServicoAlunos(AlunoRepositorio alunos, ContasDeUsuario contas, Auditoria auditoria) {
        this.alunos = alunos;
        this.contas = contas;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Pagina<Aluno> buscar(String busca, StatusAluno status, int pagina, int tamanho) {
        return alunos.buscar(busca, status, pagina, tamanho);
    }

    @Transactional(readOnly = true)
    public Aluno buscar(UUID id) {
        return alunos.porId(id).orElseThrow(() -> new NaoEncontradoException("Aluno não encontrado"));
    }

    /**
     * Cadastra o aluno nesta academia. Se o e-mail já tem conta, só liga o registro a ela (a senha
     * informada é ignorada) — a resposta é a mesma, para não revelar contas de outras academias.
     */
    @Transactional
    public Aluno criar(Aluno.Dados dados, String email, String senhaInicial) {
        if (alunos.cpfEmUso(dados.cpf(), null)) {
            throw new RegraVioladaException("CPF_JA_CADASTRADO", "CPF já cadastrado nesta academia");
        }
        UUID usuarioId = contas.garantirUsuario(email, dados.nome(), senhaInicial);
        if (alunos.usuarioJaEAluno(usuarioId)) {
            throw new RegraVioladaException("ALUNO_JA_CADASTRADO", "Este e-mail já é aluno desta academia");
        }
        Aluno aluno = Aluno.novo(usuarioId, email.strip().toLowerCase(Locale.ROOT), dados);
        alunos.salvar(aluno);
        return aluno;
    }

    @Transactional
    public Aluno atualizar(UUID id, Aluno.Dados dados) {
        Aluno aluno = buscar(id);
        if (alunos.cpfEmUso(dados.cpf(), id)) {
            throw new RegraVioladaException("CPF_JA_CADASTRADO", "CPF já cadastrado nesta academia");
        }
        aluno.atualizar(dados);
        alunos.salvar(aluno);
        return aluno;
    }

    @Transactional
    public Aluno alterarStatus(UUID id, StatusAluno novo) {
        Aluno aluno = buscar(id);
        StatusAluno anterior = aluno.status();
        if (aluno.alterarStatus(novo)) {
            alunos.salvar(aluno);
            auditoria.registrar(AcaoAuditada.ALUNO_STATUS_ALTERADO, "aluno", id,
                    Map.of("status", anterior.name()), Map.of("status", novo.name()));
        }
        return aluno;
    }

    /** "Meus dados": o registro do usuário logado na academia ativa. */
    @Transactional(readOnly = true)
    public Aluno meusDados() {
        UUID usuarioId = TenantContext.exigir().usuarioId();
        return alunos.porUsuario(usuarioId).orElseThrow(() -> new NaoEncontradoException("Aluno não encontrado"));
    }
}
