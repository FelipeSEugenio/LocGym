package com.locgym.professor;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** API pública do módulo de professores (no contexto de tenant atual). */
public interface Professores {

    record SituacaoProfessor(UUID id, String nome, boolean ativo) {}

    Optional<SituacaoProfessor> situacao(UUID professorId);

    /** Nomes dos professores informados que existem na academia; ids ausentes não aparecem no mapa. */
    Map<UUID, String> nomes(Collection<UUID> professorIds);
}
