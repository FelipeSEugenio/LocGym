package com.locgym.pagamento;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Evento interno (Spring, síncrono): uma cobrança foi paga ou abonada. Publicado dentro da transação do pagamento;
 * quem o trata (módulo de matrícula) estende a vigência antes do commit — exceção no ouvinte desfaz o pagamento.
 */
public record CobrancaQuitada(UUID cobrancaId, UUID matriculaId, LocalDate periodoInicio, LocalDate periodoFim,
                              boolean abono, LocalDate dataPagamento) {}
