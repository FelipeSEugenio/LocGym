package com.locgym.aluno.infraestrutura;

import com.locgym.aluno.dominio.Aluno;
import com.locgym.aluno.dominio.AlunoRepositorio;
import com.locgym.aluno.dominio.Cpf;
import com.locgym.aluno.dominio.StatusAluno;
import com.locgym.compartilhado.paginacao.Pagina;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
class AlunoRepositorioJpa implements AlunoRepositorio {

    private final AlunoJpaDados dados;

    AlunoRepositorioJpa(AlunoJpaDados dados) {
        this.dados = dados;
    }

    @Override
    public Pagina<Aluno> buscar(String buscaOuNulo, StatusAluno statusOuNulo, int pagina, int tamanho) {
        String padrao = null;
        String padraoCpf = null;
        if (buscaOuNulo != null && !buscaOuNulo.isBlank()) {
            padrao = "%" + buscaOuNulo.strip().toLowerCase(Locale.ROOT) + "%";
            String digitos = buscaOuNulo.replaceAll("[.\\-\\s]", "");
            padraoCpf = digitos.matches("\\d+") ? "%" + digitos + "%" : null;
        }
        var resultado = dados.buscar(padrao, padraoCpf, statusOuNulo, PageRequest.of(pagina, tamanho));
        return new Pagina<>(resultado.getContent().stream().map(AlunoJpa::paraDominio).toList(),
                pagina, tamanho, resultado.getTotalElements());
    }

    @Override
    public Optional<Aluno> porId(UUID id) {
        return dados.findById(id).map(AlunoJpa::paraDominio);
    }

    @Override
    public Optional<Aluno> porUsuario(UUID usuarioId) {
        return dados.findByUsuarioId(usuarioId).map(AlunoJpa::paraDominio);
    }

    @Override
    public boolean cpfEmUso(Cpf cpf, UUID excetoAluno) {
        return excetoAluno == null ? dados.existsByCpf(cpf.digitos()) : dados.existsByCpfAndIdNot(cpf.digitos(), excetoAluno);
    }

    @Override
    public boolean usuarioJaEAluno(UUID usuarioId) {
        return dados.existsByUsuarioId(usuarioId);
    }

    @Override
    public void salvar(Aluno aluno) {
        AlunoJpa entidade = dados.findById(aluno.id()).orElseGet(() -> new AlunoJpa(aluno.id(), aluno.usuarioId()));
        entidade.copiar(aluno);
        dados.save(entidade);
    }
}
