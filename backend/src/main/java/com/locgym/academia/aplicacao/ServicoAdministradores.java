package com.locgym.academia.aplicacao;

import com.locgym.academia.dominio.Administrador;
import com.locgym.academia.dominio.Portas.AcademiaRepositorio;
import com.locgym.academia.dominio.Portas.AdministradoresRepositorio;
import com.locgym.compartilhado.auditoria.AcaoAuditada;
import com.locgym.compartilhado.auditoria.Auditoria;
import com.locgym.compartilhado.erros.NaoEncontradoException;
import com.locgym.compartilhado.erros.RegraVioladaException;
import com.locgym.compartilhado.tenancy.TenantContext;
import com.locgym.identidade.ContasDeUsuario;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administradores da academia ativa. Todos têm o mesmo poder; a academia nunca fica sem administrador. */
@Service
public class ServicoAdministradores {

    private final AdministradoresRepositorio administradores;
    private final AcademiaRepositorio academias;
    private final ContasDeUsuario contas;
    private final Auditoria auditoria;

    public ServicoAdministradores(AdministradoresRepositorio administradores, AcademiaRepositorio academias,
                                  ContasDeUsuario contas, Auditoria auditoria) {
        this.administradores = administradores;
        this.academias = academias;
        this.contas = contas;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public List<Administrador> listar() {
        return administradores.daAcademia(academiaAtiva());
    }

    /**
     * Convida por e-mail. Se o e-mail já tem conta, só cria o vínculo (a senha informada é ignorada);
     * a resposta é a mesma nos dois casos, para não revelar contas existentes.
     */
    @Transactional
    public Administrador convidar(String nome, String email, String senhaInicial) {
        UUID academia = academiaAtiva();
        UUID usuarioId = contas.garantirUsuario(email, nome, senhaInicial);
        if (administradores.existe(usuarioId, academia)) {
            throw new RegraVioladaException("JA_E_ADMINISTRADOR", "Este e-mail já é administrador desta academia");
        }
        administradores.adicionar(usuarioId, academia, nome.strip());
        auditoria.registrar(AcaoAuditada.ADMINISTRADOR_ADICIONADO, "administrador", usuarioId, null,
                Map.of("nome", nome.strip()));
        return administradores.daAcademia(academia).stream()
                .filter(a -> a.usuarioId().equals(usuarioId)).findFirst().orElseThrow();
    }

    @Transactional
    public void remover(UUID usuarioId) {
        UUID academia = academiaAtiva();
        academias.bloquear(academia);   // dois admins removendo um ao outro não deixam a academia sem ninguém
        Administrador alvo = administradores.daAcademia(academia).stream()
                .filter(a -> a.usuarioId().equals(usuarioId)).findFirst()
                .orElseThrow(() -> new NaoEncontradoException("Administrador não encontrado"));
        if (administradores.contar(academia) <= 1) {
            throw new RegraVioladaException("ULTIMO_ADMINISTRADOR", "A academia precisa ter pelo menos um administrador");
        }
        administradores.remover(usuarioId, academia);
        auditoria.registrar(AcaoAuditada.ADMINISTRADOR_REMOVIDO, "administrador", usuarioId,
                Map.of("nome", alvo.nome()), null);
    }

    private static UUID academiaAtiva() {
        return TenantContext.exigir().academiaId();
    }
}
