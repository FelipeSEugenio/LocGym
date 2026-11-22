package com.locgym.compartilhado.auditoria;

import java.util.UUID;

/**
 * Registra uma operação crítica na mesma transação do caso de uso. {@code antes}/{@code depois} são
 * serializados em JSON — nunca passe senha ou hash.
 */
public interface Auditoria {

    /** Academia e usuário vêm do contexto de tenant da requisição. */
    void registrar(AcaoAuditada acao, String entidade, UUID entidadeId, Object antes, Object depois);

    /**
     * Para operações que acontecem fora do contexto da academia afetada (ex.: criação de uma academia).
     * Passa a transação atual a enxergar essa academia no banco (RLS) antes de gravar.
     */
    void registrarNaAcademia(UUID academiaId, UUID usuarioId, AcaoAuditada acao, String entidade, UUID entidadeId,
                             Object antes, Object depois);
}
