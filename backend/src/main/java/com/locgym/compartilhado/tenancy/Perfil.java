package com.locgym.compartilhado.tenancy;

/** Perfil de um usuário numa academia. {@link #SISTEMA} é só para rotinas internas — nunca vem de requisição. */
public enum Perfil {
    ADMIN,
    ALUNO,
    SISTEMA
}
