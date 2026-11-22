package com.locgym.compartilhado.auditoria.infraestrutura;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RegistroAuditoriaDados extends JpaRepository<RegistroAuditoria, UUID> {}
