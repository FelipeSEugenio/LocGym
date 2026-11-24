package com.locgym.academia.aplicacao;

import com.locgym.academia.ParametrosCobranca;
import com.locgym.academia.dominio.Academia;
import com.locgym.academia.dominio.DadosDaAcademia;
import com.locgym.academia.dominio.Portas.AcademiaRepositorio;
import com.locgym.academia.dominio.Portas.AdministradoresRepositorio;
import com.locgym.academia.dominio.StatusAcademia;
import com.locgym.compartilhado.auditoria.AcaoAuditada;
import com.locgym.compartilhado.auditoria.Auditoria;
import com.locgym.compartilhado.erros.NaoEncontradoException;
import com.locgym.compartilhado.erros.RegraVioladaException;
import com.locgym.compartilhado.tenancy.TenantContext;
import com.locgym.identidade.CriadorDeAcademia;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicoAcademia implements CriadorDeAcademia {

    private final AcademiaRepositorio academias;
    private final AdministradoresRepositorio administradores;
    private final Auditoria auditoria;

    public ServicoAcademia(AcademiaRepositorio academias, AdministradoresRepositorio administradores, Auditoria auditoria) {
        this.academias = academias;
        this.administradores = administradores;
        this.auditoria = auditoria;
    }

    /** Usado pelo cadastro público de administrador. */
    @Override
    @Transactional
    public UUID criarComAdministrador(NovaAcademia d, UUID usuarioId, String nomeAdministrador) {
        return criar(new DadosDaAcademia(d.nome(), d.cnpj(), d.endereco(), d.cidade(), d.estado(), d.cep(),
                d.telefone(), d.email()), usuarioId, nomeAdministrador).id();
    }

    /** Cria uma academia ATIVA com o criador como administrador. */
    @Transactional
    public Academia criar(DadosDaAcademia dados, UUID usuarioId, String nomeAdministrador) {
        if (academias.cnpjEmUso(dados.cnpj(), null)) {
            throw new RegraVioladaException("CNPJ_JA_CADASTRADO", "CNPJ já cadastrado");
        }
        Academia academia = Academia.nova(dados);
        academias.inserir(academia);
        administradores.adicionar(usuarioId, academia.id(), nomeAdministrador);
        auditoria.registrarNaAcademia(academia.id(), usuarioId, AcaoAuditada.ACADEMIA_CRIADA, "academia",
                academia.id(), null, Map.of("nome", dados.nome(), "cnpj", dados.cnpj()));
        return academia;
    }

    @Transactional(readOnly = true)
    public Academia ativa() {
        UUID id = TenantContext.exigir().academiaId();
        return academias.porId(id).orElseThrow(() -> new NaoEncontradoException("Academia não encontrada"));
    }

    /** {@code parametrosOuNulo} e {@code toleranciaOuNulo} nulos mantêm os valores atuais. */
    @Transactional
    public Academia atualizar(DadosDaAcademia dados, ParametrosCobranca parametrosOuNulo, Integer toleranciaOuNulo) {
        Academia academia = ativa();
        if (academias.cnpjEmUso(dados.cnpj(), academia.id())) {
            throw new RegraVioladaException("CNPJ_JA_CADASTRADO", "CNPJ já cadastrado");
        }
        academia.atualizar(dados);
        ParametrosCobranca anteriores = academia.parametrosCobranca();
        if (parametrosOuNulo != null && academia.alterarParametrosCobranca(parametrosOuNulo)) {
            auditoria.registrar(AcaoAuditada.PARAMETROS_COBRANCA_ALTERADOS, "academia", academia.id(),
                    anteriores, parametrosOuNulo);
        }
        int toleranciaAnterior = academia.toleranciaPontualidade();
        if (toleranciaOuNulo != null && academia.alterarToleranciaPontualidade(toleranciaOuNulo)) {
            auditoria.registrar(AcaoAuditada.TOLERANCIA_PONTUALIDADE_ALTERADA, "academia", academia.id(),
                    java.util.Map.of("toleranciaPontualidade", toleranciaAnterior),
                    java.util.Map.of("toleranciaPontualidade", toleranciaOuNulo));
        }
        academias.atualizar(academia);
        return academia;
    }

    @Transactional
    public Academia alterarStatus(StatusAcademia novo) {
        Academia academia = ativa();
        StatusAcademia anterior = academia.status();
        if (academia.alterarStatus(novo)) {
            academias.atualizar(academia);
            auditoria.registrar(AcaoAuditada.ACADEMIA_STATUS_ALTERADO, "academia", academia.id(),
                    Map.of("status", anterior.name()), Map.of("status", novo.name()));
        }
        return academia;
    }
}
