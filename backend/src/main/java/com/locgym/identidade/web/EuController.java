package com.locgym.identidade.web;

import com.locgym.identidade.dominio.Portas.VinculosRepositorio;
import com.locgym.identidade.web.SessaoResposta.VinculoResposta;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/eu")
class EuController {

    private final VinculosRepositorio vinculos;

    EuController(VinculosRepositorio vinculos) {
        this.vinculos = vinculos;
    }

    /** "Minhas academias": vínculos atuais do usuário logado (para o seletor de academia). */
    @GetMapping("/vinculos")
    List<VinculoResposta> vinculos(@AuthenticationPrincipal Jwt jwt) {
        return vinculos.doUsuario(UUID.fromString(jwt.getSubject())).stream().map(VinculoResposta::de).toList();
    }
}
