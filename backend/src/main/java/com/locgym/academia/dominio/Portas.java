package com.locgym.academia.dominio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class Portas {

    private Portas() {}

    public interface AcademiaRepositorio {
        Optional<Academia> porId(UUID id);

        /** Bloqueia a linha da academia até o fim da transação (serializa mudanças de administradores). */
        void bloquear(UUID id);

        boolean cnpjEmUso(String cnpj, UUID excetoAcademia);

        void inserir(Academia academia);

        void atualizar(Academia academia);
    }

    public interface AdministradoresRepositorio {
        List<Administrador> daAcademia(UUID academiaId);

        boolean existe(UUID usuarioId, UUID academiaId);

        int contar(UUID academiaId);

        void adicionar(UUID usuarioId, UUID academiaId, String nome);

        void remover(UUID usuarioId, UUID academiaId);
    }
}
