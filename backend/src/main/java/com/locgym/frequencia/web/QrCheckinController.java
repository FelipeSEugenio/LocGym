package com.locgym.frequencia.web;

import com.locgym.frequencia.aplicacao.ServicoTokenQr;
import com.locgym.frequencia.aplicacao.ServicoTokenQr.TokenEmitido;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** "Meu QR Code" do portal do aluno (perfil ALUNO, como todo {@code /api/v1/eu/**}). */
@RestController
class QrCheckinController {

    private final ServicoTokenQr servico;

    QrCheckinController(ServicoTokenQr servico) {
        this.servico = servico;
    }

    @GetMapping("/api/v1/eu/qr-checkin")
    TokenEmitido meuQr() {
        return servico.emitirParaUsuarioAtual();
    }
}
