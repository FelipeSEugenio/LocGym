package com.locgym.academia.dominio;

import java.util.UUID;

/** Administrador de uma academia. {@code nome} é o nome informado para esta academia (não o global). */
public record Administrador(UUID usuarioId, String nome, String email) {}
