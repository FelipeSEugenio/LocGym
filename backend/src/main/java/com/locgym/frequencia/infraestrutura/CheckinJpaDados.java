package com.locgym.frequencia.infraestrutura;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CheckinJpaDados extends JpaRepository<CheckinJpa, UUID> {}
