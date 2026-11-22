package com.locgym.aluno.dominio;

/**
 * Status do aluno na academia, independente da matrícula. ATIVO e INATIVO mantêm o acesso ao portal
 * (INATIVO só consulta); CANCELADO perde o vínculo com a academia.
 */
public enum StatusAluno { ATIVO, INATIVO, CANCELADO }
