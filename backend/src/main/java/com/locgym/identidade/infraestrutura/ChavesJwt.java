package com.locgym.identidade.infraestrutura;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Par de chaves RSA que assina os JWT. Lido de {@code locgym.jwt.chave-privada} (PEM PKCS#8) quando
 * configurado; senão gerado na subida — os tokens não sobrevivem a um reinício (aceitável em dev).
 */
@Component
class ChavesJwt {

    private static final Logger log = LoggerFactory.getLogger(ChavesJwt.class);

    private final RSAPrivateCrtKey privada;
    private final RSAPublicKey publica;

    ChavesJwt(@Value("${locgym.jwt.chave-privada:}") String pem) throws Exception {
        KeyFactory fabrica = KeyFactory.getInstance("RSA");
        if (pem == null || pem.isBlank()) {
            log.warn("locgym.jwt.chave-privada não configurada: gerando chave RSA temporária "
                    + "(sessões não sobrevivem a reinícios). Configure JWT_CHAVE_PRIVADA fora de desenvolvimento.");
            KeyPair par = gerar();
            this.privada = (RSAPrivateCrtKey) par.getPrivate();
        } else {
            String base64 = pem.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").replaceAll("\\s", "");
            this.privada = (RSAPrivateCrtKey) fabrica.generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
        }
        this.publica = (RSAPublicKey) fabrica.generatePublic(
                new RSAPublicKeySpec(privada.getModulus(), privada.getPublicExponent()));
    }

    private static KeyPair gerar() throws NoSuchAlgorithmException {
        KeyPairGenerator gerador = KeyPairGenerator.getInstance("RSA");
        gerador.initialize(2048);
        return gerador.generateKeyPair();
    }

    RSAPrivateCrtKey privada() { return privada; }
    RSAPublicKey publica() { return publica; }
}
