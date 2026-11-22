package com.locgym.identidade.infraestrutura;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface UsuarioJpaDados extends JpaRepository<UsuarioJpa, UUID> {
    Optional<UsuarioJpa> findByEmail(String email);
}
