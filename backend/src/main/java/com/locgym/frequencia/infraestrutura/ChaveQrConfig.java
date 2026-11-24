package com.locgym.frequencia.infraestrutura;

import com.locgym.frequencia.dominio.TokenCheckin;
import java.security.SecureRandom;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chave HMAC do QR de check-in: Base64 em {@code locgym.checkin.chave-qr} (env {@code CHECKIN_CHAVE_QR}, ≥ 32 bytes).
 * Vazia: gera uma temporária a cada subida (só desenvolvimento; QRs emitidos antes de um reinício deixam de valer).
 */
@Configuration
class ChaveQrConfig {

    private static final Logger log = LoggerFactory.getLogger(ChaveQrConfig.class);

    @Bean
    TokenCheckin tokenCheckin(@Value("${locgym.checkin.chave-qr:}") String chaveBase64) {
        if (chaveBase64 == null || chaveBase64.isBlank()) {
            log.warn("locgym.checkin.chave-qr não configurada: gerando chave do QR temporária (só desenvolvimento)");
            byte[] chave = new byte[32];
            new SecureRandom().nextBytes(chave);
            return new TokenCheckin(chave);
        }
        return new TokenCheckin(Base64.getDecoder().decode(chaveBase64.strip()));
    }
}
