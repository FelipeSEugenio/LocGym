package com.locgym.compartilhado.auditoria;

import java.util.UUID;

/**
 * Registra uma operação crítica na mesma transação do caso de uso. Academia e usuário vêm do contexto
 * de tenant. {@code antes}/{@code depois} são serializados em JSON — nunca passe senha ou hash.
 */
public interface Auditoria {

    void registrar(AcaoAuditada acao, String entidade, UUID entidadeId, Object antes, Object depois);
}
