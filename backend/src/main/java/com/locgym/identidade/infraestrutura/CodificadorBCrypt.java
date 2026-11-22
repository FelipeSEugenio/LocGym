package com.locgym.identidade.infraestrutura;

import com.locgym.identidade.dominio.Portas.CodificadorDeSenha;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
class CodificadorBCrypt implements CodificadorDeSenha {

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    /** Hash usado quando o usuário não existe, para o login custar o mesmo tempo. */
    private final String hashFicticio = bcrypt.encode("senha-ficticia-para-tempo-constante");

    @Override
    public String codificar(String senha) {
        return bcrypt.encode(senha);
    }

    @Override
    public boolean confere(String senha, String hashOuNulo) {
        boolean resultado = bcrypt.matches(senha == null ? "" : senha, hashOuNulo != null ? hashOuNulo : hashFicticio);
        return hashOuNulo != null && resultado;
    }
}
