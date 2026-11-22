package com.locgym.identidade;

import java.util.UUID;

/** API pública do módulo de identidade para criar contas de login (usada por academia e aluno). */
public interface ContasDeUsuario {

    /**
     * Cria um usuário novo com a senha informada.
     *
     * @throws com.locgym.compartilhado.erros.RegraVioladaException {@code EMAIL_JA_CADASTRADO} se o e-mail já existe
     */
    UUID criarUsuario(String email, String nome, String senha);

    /**
     * Devolve o usuário do e-mail, criando-o se não existir. Usuário novo recebe a senha inicial e
     * precisa trocá-la no primeiro acesso; usuário existente mantém a senha atual (a informada é ignorada).
     * O chamador não consegue distinguir os dois casos.
     */
    UUID garantirUsuario(String email, String nome, String senhaInicial);
}
